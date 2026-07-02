# Contributing to papazon-dash

Thanks for your interest in papazon-dash!

## How to contribute

Feel free to **fork, modify, and use** this project however you like.  
Pull requests are welcome if you want to share improvements upstream.

### Workflow

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Push your branch and open a Pull Request

For **significant changes** (new features, architecture changes, breaking API modifications),  
please **open an Issue first** to discuss your proposal. This helps avoid duplicate work  
and ensures alignment before you invest time in a large implementation.

## Code style

- Kotlin: follow [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- TypeScript (Cloud Functions): follow the existing `eslint` configuration in `functions/`
- Run `./gradlew ktlintCheck` (if configured) before submitting Android changes
- Run `npm run lint` inside `functions/` before submitting Cloud Functions changes

## Reporting bugs

Open a GitHub Issue with:
- Steps to reproduce
- Expected vs actual behavior
- Android version / device model (if relevant)
- Logcat output (if available)

## License

By contributing, you agree that your contributions will be licensed under the  
[MIT License](LICENSE) that covers this project.
