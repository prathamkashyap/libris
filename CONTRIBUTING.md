# Contributing to Libris

First off, thank you for considering contributing to Libris! It's people like you that make this library management system a great tool for educational institutions.

## Code of Conduct

By participating in this project, you are expected to uphold our Code of Conduct. Please be respectful and considerate of others.

## How Can I Contribute?

### Reporting Bugs

- Ensure the bug was not already reported by searching on GitHub under [Issues](https://github.com/prathamkashyap/libris/issues).
- If you're unable to find an open issue addressing the problem, open a new one. Be sure to include a title and clear description, as much relevant information as possible, and a code sample or an executable test case demonstrating the expected behavior that is not occurring.

### Suggesting Enhancements

- Open a new issue with a clear title and description of the proposed enhancement.
- Explain why this enhancement would be useful to most Libris users.

### Pull Requests

1. Fork the repo and create your branch from `main`.
2. If you've added code that should be tested, add tests.
3. If you've changed APIs, update the documentation.
4. Ensure the test suite passes (`./mvnw clean verify`).
5. Make sure your code passes the Spotless formatting checks (`./mvnw spotless:check`).
6. Issue that pull request!

## Styleguides

### Git Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters or less
- Reference issues and pull requests liberally after the first line

#### Automated Tool Attribution

- Commit messages must not include automated co-author trailers or tool-generated attribution lines. Do not add agent, bot, AI assistant, or automated-integration identities in `Co-Authored-By` trailers, or generated footers such as `Generated with [ToolName]`.
- Keep author and committer metadata accurate. This policy does not prohibit legitimate human co-author attribution or authorize changing authorship metadata.
- Preserve meaningful implementation details, test results, issue references, and human attribution.
- Before committing, inspect the full message, including its body and trailers, for automated tool attribution.

### Java Code Style

We use Google Java Format. Run `./mvnw spotless:apply` before committing to ensure your code matches the project style.

### Frontend Code Style

- Use ES Modules (`<script type="module">`).
- Keep components modular (e.g., `components/sidebar.html`).
- Respect user preferences like `prefers-reduced-motion` in CSS.
