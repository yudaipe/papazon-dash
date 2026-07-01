import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp, type DocumentData, type QueryDocumentSnapshot } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger, setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  buildDataMessage,
  displayItemName,
  recipientForCompletedItem,
  recipientForCreatedItem,
  recipientForReminder,
  type ItemData,
  type PairData,
} from "./notifications";

initializeApp();
setGlobalOptions({
  region: "asia-northeast1",
  maxInstances: 5,
  timeoutSeconds: 60,
  memory: "256MiB",
});

const PAIR_PATH = "pairs/{pairId}";
const ITEM_PATH = "pairs/{pairId}/items/{itemId}";

// CTL-3 fix: pair doc が active=false に更新されたら items サブコレクションを cascade 削除
// CTL-6 fix: deactivated pair doc の後始末（active=false への遷移をトリガー）
// 対応バグ: FirebaseRepository.unpair() が Firestore を変更しなかった旧実装
export const onPairDeactivated = onDocumentUpdated(PAIR_PATH, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after) return;
  if (before.active !== true || after.active !== false) return;

  const pairId = event.params.pairId;
  logger.info("onPairDeactivated: cascade deleting items", { pairId });

  const db = getFirestore();
  const itemsSnap = await db.collection(`pairs/${pairId}/items`).get();
  if (itemsSnap.empty) {
    logger.info("onPairDeactivated: no items", { pairId });
    return;
  }

  const batch = db.batch();
  for (const doc of itemsSnap.docs) {
    batch.delete(doc.ref);
  }
  await batch.commit();
  logger.info("onPairDeactivated: items deleted", { pairId, count: itemsSnap.size });
});

export const onItemCreated = onDocumentCreated(ITEM_PATH, async (event) => {
  const item = event.data?.data() as ItemData | undefined;
  if (!item) {
    logger.warn("onItemCreated skipped: missing item data", event.params);
    return;
  }

  const pairId = event.params.pairId;
  const pair = await readPair(pairId);
  const recipientUid = recipientForCreatedItem(pair, item);
  if (!recipientUid) {
    logger.warn("onItemCreated skipped: recipient uid missing", { pairId, itemId: event.params.itemId });
    return;
  }

  await sendToUser(recipientUid, {
    type: "item_created",
    itemId: event.params.itemId,
    pairId,
    itemName: displayItemName(item),
    priority: "high",
  });
});

export const onItemCompleted = onDocumentUpdated(ITEM_PATH, async (event) => {
  const before = event.data?.before.data() as ItemData | undefined;
  const after = event.data?.after.data() as ItemData | undefined;
  if (!before || !after) {
    logger.warn("onItemCompleted skipped: missing before/after data", event.params);
    return;
  }

  if (before.status === after.status || after.status !== "done") {
    return;
  }

  const pairId = event.params.pairId;
  const pair = await readPair(pairId);
  const recipientUid = recipientForCompletedItem(pair);
  if (!recipientUid) {
    logger.warn("onItemCompleted skipped: master uid missing", { pairId, itemId: event.params.itemId });
    return;
  }

  await sendToUser(recipientUid, {
    type: "item_completed",
    itemId: event.params.itemId,
    pairId,
    itemName: displayItemName(after),
    priority: "normal",
  });
});

export const reminderSchedule = onSchedule(
  {
    schedule: "every 1 minutes",
    timeZone: "Asia/Tokyo",
  },
  async () => {
    const db = getFirestore();
    const now = Timestamp.now();
    const snapshot = await db.collectionGroup("items")
      .where("status", "==", "open")
      .where("reminder_at", "<=", now)
      .limit(100)
      .get();

    logger.info("reminderSchedule scan completed", { count: snapshot.size });

    for (const doc of snapshot.docs) {
      await sendReminderForItem(doc);
    }
  },
);

async function sendReminderForItem(doc: QueryDocumentSnapshot<DocumentData>): Promise<void> {
  const item = doc.data() as ItemData & { pairId?: string };
  const pairId = item.pairId ?? doc.ref.parent.parent?.id;
  if (!pairId) {
    logger.warn("reminder skipped: pair id missing", { itemId: doc.id, path: doc.ref.path });
    return;
  }

  const pair = await readPair(pairId);
  const recipientUid = recipientForReminder(pair);
  if (!recipientUid) {
    logger.warn("reminder skipped: slave uid missing", { pairId, itemId: doc.id });
    return;
  }

  const sent = await sendToUser(recipientUid, {
    type: "reminder",
    itemId: doc.id,
    pairId,
    itemName: displayItemName(item),
    priority: "high",
  });

  if (sent) {
    await doc.ref.update({ reminder_at: null });
  }
}

async function readPair(pairId: string): Promise<PairData> {
  const pairSnap = await getFirestore().doc(`pairs/${pairId}`).get();
  return (pairSnap.data() ?? {}) as PairData;
}

async function sendToUser(
  uid: string,
  params: {
    type: "item_created" | "item_completed" | "reminder";
    itemId: string;
    pairId: string;
    itemName: string;
    priority: "high" | "normal";
  },
): Promise<boolean> {
  const userSnap = await getFirestore().doc(`users/${uid}`).get();
  const token = userSnap.data()?.fcmToken;
  if (typeof token !== "string" || token.trim().length === 0) {
    logger.warn("FCM send skipped: token missing", { uid, type: params.type, itemId: params.itemId });
    return false;
  }

  const message = buildDataMessage({
    token,
    type: params.type,
    itemId: params.itemId,
    pairId: params.pairId,
    itemName: params.itemName,
    priority: params.priority,
  });

  const messageId = await getMessaging().send(message);
  logger.info("FCM sent", { messageId, uid, type: params.type, itemId: params.itemId, pairId: params.pairId });
  return true;
}
