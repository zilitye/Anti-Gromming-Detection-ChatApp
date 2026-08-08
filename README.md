## Anti-Gromming-Detection-ChatApp

The significance of this application extends beyond individual protection. By logging flagged 
incidents to a secure cloud backend (Firebase Firestore), the system creates an auditable record of grooming-related communications that can support institutional reporting, counselling referrals, and policy development. The Risk Dashboard feature allows users to visualise the cumulative risk profile of a conversation over time, transforming individual message-level detection into a holistic conversation-level safety assessment.

## Demo

[![Download](https://img.shields.io/badge/Download-v1.2.0-blue?style=flat-square)](https://github.com/zilitye/Anti-Gromming-Detection-ChatApp/releases/download/v1.2.0/app-release.apk)

Install and run `app-release.apk`. Requires Android 7.0 or higher

![alt text](image-1.png)

## Features

1.  Real-time grooming detection: Analysing outgoing messages for language patterns 
associated with grooming behaviour before they are sent, using a rule-based keyword 
scoring engine. 

2.  AI-powered survivor support: Providing a dedicated Safety Hub chatbot, powered by 
Google Gemini, that offers survivors compassionate, context-aware guidance, reporting 
pathways, and emotional support. 

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java (Android SDK) |
| UI | XML layouts, View Binding, RecyclerView |
| Database | Firebase Firestore |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| AI / LLM | Google Gemini API (`gemini-flash-latest`) |
| HTTP Client | Retrofit 2 |
| Markdown Rendering | Markwon |

## Setup & Installation

1. Clone the repository
```bash
git clone https://github.com/your-username/Anti-Gromming-Detection-ChatApp.git
cd Anti-Gromming-Detection-ChatApp
```

2. Connect Firebase
- Create a project at Firebase Console
- Enable **Firestore Database** and **Cloud Messaging**
- Download `google-services.json` and place it in `app/`
- Navigate to Project Settings
- Select the Service Account tab
- Click on Generate New Private Key to download the `service_account.json` file
- Place `service_account.json` it inside your app’s `app/src/main/assets` directory in Android Studio

3. Add your Gemini API key
- Open `app/src/main/java/com/example/chatapp/utilities/Constants.java` and replace:
```java
public static final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE";
```

4. Build and run
- Open the project in Android Studio

