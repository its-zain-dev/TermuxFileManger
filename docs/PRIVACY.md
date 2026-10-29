# Privacy information — Termux File Manager

*Applies to the open-source Android application. Last reviewed for source version 0.5.0. This document describes behavior visible in the published source; external providers set their own policies.*

## Information processed by the application

TFM accesses files and executes commands through Termux only after the user grants the Android Termux command permission and completes setup. File lists, edited files, console output, local chat history, workspace details and settings are stored or processed on the device or within the Termux environment as required for the features selected by the user.

## Network requests and optional AI services

The AI assistant is optional. When a user sends a request to an AI provider, TFM sends the request content, conversation context needed for the request, and explicitly selected attachments or tool results to the configured provider's HTTPS endpoint. The provider may receive these data and apply its own privacy, retention and billing policies. Supported providers include commercial services; users should review the provider terms before use.

If the user chooses ChatGPT sign-in, authentication uses OpenAI's browser-based authorization service, and subsequent API requests use the authorized account and endpoint. Other AI provider connections may require a user-supplied API key. Network features such as web research, skill import, model discovery and HTTPS downloads contact external services only when those features are used.

## Credentials, media and execution

The project uses Android Keystore-backed storage for AI credentials and account tokens, according to the source. A screen capture requires Android's screen capture authorization, and screenshots/images are only sent to an AI endpoint when deliberately attached or included in a user-approved request. Shell commands run with the permissions of the connected Termux app, and may read or modify files; dangerous operations should be reviewed before approval.

## Analytics, advertising and third parties

The reviewed Gradle dependencies do not include Google Play Services, Firebase or an advertising/analytics SDK. This is not a claim about data collection performed by independently operated AI services or other external endpoints. The application is not itself an AI cloud host.

## Control and contact

Users can avoid external AI requests by not configuring or using the assistant. They can remove configured API keys, disconnect accounts using app settings, and uninstall the app to remove application-private data, subject to Android and Termux storage behavior. Termux user files may remain separately.

Issues and privacy questions: https://github.com/its-zain-dev/TermuxFileManger/issues

The maintained source code and release history are published at:
https://github.com/its-zain-dev/TermuxFileManger
