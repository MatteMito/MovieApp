# MovieApp

**An Android app to manage, share and analyse your film lists, with a NestJS back end.**
Bachelor's thesis project, University of Bologna (BSc in Computer Science for Management, 2025). Designed and built solo, app and server.

> 🇮🇹 *Applicazione mobile per la gestione e l'analisi personalizzata di liste di film, con dati importati da IMDb e Letterboxd. Progetto di tesi triennale.*

<!-- Add 3-4 screenshots here: create a /screenshots folder and uncomment
<p align="center">
  <img src="screenshots/home.png" width="220">
  <img src="screenshots/stats.png" width="220">
  <img src="screenshots/lists.png" width="220">
</p>
-->

## Features

- **Import your history** from IMDb and Letterboxd CSV exports, processed in the background
- **Automatic enrichment** of every film with metadata from The Movie Database (TMDB) API
- **Personal statistics**: genres, directors and viewing habits shown as charts
- **Lists**: create, edit, make public, copy other users' lists and follow them
- **Real-time notifications** when a list you follow changes, over WebSocket
- **Accounts** with registration, login and JWT-protected API

## Architecture

```
Android app (Kotlin)  ──REST + WebSocket──▶  NestJS API (TypeScript)  ──▶  PostgreSQL
                                                   │
                                                   └──▶  TMDB API (scheduled sync + cache)
```

| Layer | Technologies |
|---|---|
| Android app | Kotlin, MVVM (ViewModel + LiveData), Navigation, Retrofit/OkHttp, Coroutines, WorkManager, MPAndroidChart, Glide, Socket.IO client |
| Back end | NestJS, TypeORM, PostgreSQL, Passport JWT, bcrypt, Socket.IO, @nestjs/schedule |
| External data | TMDB API, IMDb and Letterboxd CSV exports |

Back-end modules: `auth`, `movies`, `lists`, `analytics`, `tmdb`, `websocket`.

## Running it locally

**Requirements:** Node.js 18+, PostgreSQL, Android Studio, and a free TMDB API key from [themoviedb.org](https://www.themoviedb.org/settings/api).

### Back end

```bash
cd MovieAppFrontBack/movieapp-backend
cp .env.example .env      # then fill in DB_PASSWORD, JWT_SECRET and TMDB_API_KEY
npm install
createdb -U postgres movieapp
psql -U postgres -d movieapp -f migrations/schema.sql
npm run start:dev
```

The API starts on port 3001.

### Android app

Open `MovieAppFrontBack/MovieApp` in Android Studio. In `config/AppConfig.kt`, set `BACKEND_HOST` to the IP address of the computer running the back end (use `10.0.2.2` on the Android emulator), then run the app on an emulator or device.

## Author

**Matteo Boscherini** · [LinkedIn](https://www.linkedin.com/in/matteo-boscherini-664569225/) · matteo.boscherini17@gmail.com
