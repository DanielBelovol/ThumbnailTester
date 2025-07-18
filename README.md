# ThumbnailTester & Data Structures Documentation

This document provides a comprehensive overview of the WebSocket API and data structures used by the ThumbnailTester backend service.

---

## WebSocket Endpoint

- **URL:** `/ws`
- **Protocol:** STOMP over WebSocket (with SockJS fallback)
- **Send messages to:**
    - `/app/thumbnail/test` — Start a thumbnail test
    - `/app/remove-testingItem` — Remove a thumbnail or text item from the testing queue if it has not yet been tested and is not currently being tested
- **Subscribe to topics:**
    - `/topic/thumbnail/error` — Error messages
    - `/topic/thumbnail/progress` — Progress updates during testing
    - `/topic/thumbnail/final` — Final test results
    - `/topic/thumbnail/result` — Scheduled test results after test duration

---

## Request DTOs

### `ThumbnailRequest`

Request sent by the client to initiate a thumbnail test.

| Field            | Type                      | Description                                         |
|------------------|---------------------------|-----------------------------------------------------|
| `images`         | `List<String>`            | List of image URLs to be tested                      |
| `texts`          | `List<String>`            | List of texts corresponding to images (optional)   |
| `videoUrl`       | `String`                  | YouTube video URL to test thumbnails on             |
| `testConfRequest`| `ThumbnailTestConfRequest`| Configuration settings for the test                  |
| `userDTO`        | `UserRequest`             | User credentials (Google ID and refresh token)      |

---

### `ThumbnailTestConfRequest`

Configuration details for the thumbnail test.

| Field                  | Type     | Description                                                                                  |
|------------------------|----------|----------------------------------------------------------------------------------------------|
| `testingType`          | `String` | Type of test (`THUMBNAIL`, `TEXT`, `THUMBNAIL+TEXT`)                                         |
| `testingMode`          | `String` | Testing mode (e.g., `TIME_BASED`, `METRICS_BASED(NOW IS NOT WORKING!!)`)                     |
| `testingByTimeMinutes` | `long`   | Duration of the test in minutes                                                              |
| `testingByMetrics`     | `long`   | Metric-based testing parameter (NOT WORKING NOW!!)                                           |
| `criterionOfWinner`    | `String` | Criterion to select winner (`NONE`, `VIEWS`, `AVD`, `CTR`,`WATCH_TIME`, `CTR_AVD(MULTIPLE)`) |

---

### `UserRequest`

User authentication data.

| Field          | Type   | Description                  |
|----------------|--------|------------------------------|
| `googleId`     | String | Google user ID               |
| `refreshToken` | String | OAuth2 refresh token         |

---

## Data Transfer Objects (DTOs) Sent from Backend

### `ImageOption`

Represents an image option in the test.

| Field           | Type             | Description                                  |
|-----------------|------------------|----------------------------------------------|
| `id`            | `Long`           | Unique identifier                            |
| `fileUrl`       | `String`         | URL of the image                             |
| `text`          | `String`         | Optional text associated with the image     |
| `isWinner`      | `boolean`        | Indicates if this option is the winner       |
| `thumbnailStats`| `ThumbnailStats`  | Analytics stats related to this image        |
| `thumbnail`     | `ThumbnailData`   | Reference to parent thumbnail data|

---

### `ThumbnailStats`

Analytics statistics for an image option.

| Field                 | Type     | Description                                 |
|-----------------------|----------|---------------------------------------------|
| `id`                  | `Long`   | Unique identifier                           |
| `imageOption`          | `ImageOption` | Back-reference to image option|
| `views`               | `Integer`| Number of views                             |
| `ctr`                 | `Double` | Click-through rate                          |
| `impressions`         | `Integer`| Number of impressions                       |
| `averageViewDuration` | `Double` | Average view duration (seconds)             |
| `advCtr`              | `Double` | Advanced CTR metric                          |
| `comments`            | `Integer`| Number of comments                          |
| `shares`              | `Integer`| Number of shares                            |
| `likes`               | `Integer`| Number of likes                             |
| `subscribersGained`   | `Integer`| Number of subscribers gained                |
| `averageViewPercentage` | `Double` | Average percentage of video watched         |
| `totalWatchTime`      | `Long`   | Total watch time in seconds                  |

---

### `ThumbnailData`

Represents the overall thumbnail test data. Contains information about the video, associated image options, test configuration, and the user who initiated the test.

| Field          | Type                 | Description                                                                                  |
|----------------|----------------------|----------------------------------------------------------------------------------------------|
| `id`           | `Long`               | Unique identifier of the thumbnail test record                                               |
| `imageOptions` | `List<ImageOption>`  | List of image options participating in the test                                             |
| `videoUrl`     | `String`             | YouTube video URL for which the test is conducted                                           |
| `testConf`     | `ThumbnailTestConf`  | Test configuration (test type, criteria, parameters)                                        |
| `user`         | `UserData`           | Information about the user who initiated the test                                           |

---

### Example JSON (approximate structure)

```json
{
  "id": 456,
  "imageOptions": [
    {
      "id": 123,
      "fileUrl": "https://example.com/image1.png",
      "text": "Sample text",
      "isWinner": false,
      "thumbnailStats": {
        "views": 1000,
        "ctr": 5.5,
        "impressions": 18000,
        "averageViewDuration": 45.3,
        "advCtr": 3.2,
        "comments": 10,
        "shares": 5,
        "likes": 100,
        "subscribersGained": 2,
        "averageViewPercentage": 60.0,
        "totalWatchTime": 45000
      }
    }
  ],
  "videoUrl": "https://www.youtube.com/watch?v=VIDEO_ID",
  "testConf": {
    "id": 789,
    "testType": "THUMBNAILTEXT",
    "testingMode": "TIME_BASED",
    "testingByTimeMinutes": 10,
    "criterionOfWinner": "VIEWS"
  },
  "user": {
    "id": 101,
    "googleId": "user-google-id"
  }
}
```

---

### Notes

- `imageOptions` contains all images involved in the test along with their statistics.
- `testConf` holds the test settings including type and parameters.
- `user` contains minimal user data related to the test (usually IDs and Google ID).

---

## Example JSON Payloads

### Example `ThumbnailRequest`

```json
{
  "images": [
    "https://example.com/image1.png",
    "https://example.com/image2.png"
  ],
  "texts": [
    "Text for image 1",
    "Text for image 2"
  ],
  "videoUrl": "https://www.youtube.com/watch?v=VIDEO_ID",
  "testConfRequest": {
    "testingType": "THUMBNAILTEXT",
    "testingMode": "TIME_BASED",
    "testingByTimeMinutes": 10,
    "testingByMetrics": 0,
    "criterionOfWinner": "VIEWS"
  },
  "userDTO": {
    "googleId": "user-google-id",
    "refreshToken": "user-refresh-token"
  }
}
```

### Example `ImageOption` (from backend)

```json
{
  "id": 123,
  "fileUrl": "https://example.com/image1.png",
  "text": "Sample text",
  "isWinner": false,
  "thumbnailStats": {
    "views": 1000,
    "ctr": 5.5,
    "impressions": 18000,
    "averageViewDuration": 45.3,
    "advCtr": 3.2,
    "comments": 10,
    "shares": 5,
    "likes": 100,
    "subscribersGained": 2,
    "averageViewPercentage": 60.0,
    "totalWatchTime": 45000
  }
}
```

---

## Environment Configuration

To run the application correctly, you need to edit a `.env` file in the root directory of the project:

```env
# Application name
APPLICATION_NAME=ThumbnailTester
SPRING_APPLICATION_NAME=ThumbnailTester

# Database connection settings
SPRING_DATASOURCE_URL=jdbc:postgresql://thumbnail-test-db:5432/thumbnailTester
SPRING_DATASOURCE_USERNAME=DATABASE_USER
SPRING_DATASOURCE_PASSWORD=DATABASE_PASSWORD

# Flyway migration settings
SPRING_FLYWAY_URL=jdbc:postgresql://thumbnail-test-db:5432/thumbnailTester
SPRING_FLYWAY_USER=DATABASE_USER
SPRING_FLYWAY_PASSWORD=DATABASE_PASSWORD

# YouTube API credentials
YOUTUBE_CLIENT_ID=YOUR_CLIENT_ID
YOUTUBE_CLIENT_SECRET=YOUR_YOUTUBE_CLIENT_SECRET

# AES Encryption Key
# The application expects Google ID and refresh token to be AES-encrypted.
# Provide the AES key here (must match the key used by the client for encryption).
KEY_AES=your-32-byte-aes-key-here
```

> **Important:**  
> Replace the values with those appropriate for your environment — database URLs, usernames, passwords, and YouTube API credentials.

---

This file is used to configure the application and load environment variables at startup. Make sure your environment or build tools properly load these variables.

---

## Usage Flow

1. **Connect** to the WebSocket endpoint `/ws` using STOMP.
2. **Send** a thumbnail test request to `/app/thumbnail/test` with the required payload.
3. **Subscribe** to the following topics to receive updates:
    - `/topic/thumbnail/error` — error notifications
    - `/topic/thumbnail/progress` — progress updates during testing
    - `/topic/thumbnail/final` — final test results
    - `/topic/thumbnail/result` — scheduled test results after test duration
4. **Handle** incoming messages appropriately in the frontend UI.
5. To **remove** a testing item, send a message to `/app/remove-testingItem` with the image option ID and video URL.

---

## Running with Docker

If you want to run the application using Docker, make sure you have created the `.env` file as described above, then start the containers with the following command:

```bash
docker-compose --env-file .env up --build
```

This command will build and start the Docker containers using the environment variables defined in your `.env` file.

---

## Important Notes

- All messages are JSON encoded.
- Errors are sent as simple string messages on the error topic.
- The backend expects valid YouTube video URLs and accessible image URLs.
- Image files must be less than 2 MB and have a 16:9 aspect ratio.
- User credentials (`googleId` and `refreshToken`) are required for YouTube API authentication.

---

## YouTube OAuth2 Token Exchange API

This application provides an API endpoint to exchange a Google OAuth2 authorization code for a refresh token. This allows your application to obtain long-lived access to YouTube APIs on behalf of a user.

### Endpoint

`POST /api/youtube/exchange-code`

### Description

Exchanges an authorization code obtained from Google OAuth2 for a refresh token.

### Request

- **Content-Type:** `application/x-www-form-urlencoded`
- **Parameters:**
    - `code` (string, required): The authorization code received from Google OAuth2 after user consent.

### Response

Returns a JSON object with the following fields:

| Field          | Type   | Description                                  |
|----------------|--------|----------------------------------------------|
| `refreshToken` | String | The refresh token if the exchange was successful; otherwise `null`. |
| `message`      | String | Status message indicating success or error details. |

### Example Request

```bash
curl -X POST "http://localhost:8080/api/youtube/exchange-code" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "code=YOUR_AUTHORIZATION_CODE"
```

### Example Response (Success)

```json
{
  "refreshToken": "1//0gL...your_refresh_token...",
  "message": "Successful"
}
```

### Example Response (Error)

```json
{
  "refreshToken": null,
  "message": "invalid_grant: Bad Request"
}
```

### Configuration

Add the following properties to your `application.properties` or `application.yml` file:

```properties
youtube.client.id=YOUR_GOOGLE_CLIENT_ID
youtube.client.secret=YOUR_GOOGLE_CLIENT_SECRET
youtube.redirect.uri=YOUR_REGISTERED_REDIRECT_URI
```

- `youtube.client.id`: Your Google OAuth2 client ID.
- `youtube.client.secret`: Your Google OAuth2 client secret.
- `youtube.redirect.uri`: The redirect URI registered in your Google API Console.

### Notes

- The endpoint uses Java's built-in `HttpClient` to communicate with Google's OAuth2 token endpoint.
- The refresh token is only returned if the authorization code is valid and the client credentials are correct.
- Handle the refresh token securely as it grants long-term access to the user's YouTube account.

---

If you want me to help you generate a full standalone README or add more sections, just ask!