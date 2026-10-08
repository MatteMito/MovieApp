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

### Back end

```bash
cd MovieAppFrontBack/movieapp-backend
cp .env.example .env      # then fill in your own values
npm install
psql -U postgres -f migrations/schema.sql
npm run start:dev
```

You need a free TMDB API key from [themoviedb.org](https://www.themoviedb.org/settings/api).

### Android app

Open `MovieAppFrontBack/MovieApp` in Android Studio, set the back-end address in `config/AppConfig.kt` and run it on an emulator or device.

## Author

**Matteo Boscherini** · [LinkedIn](https://www.linkedin.com/in/matteo-boscherini-664569225/) · matteo.boscherini17@gmail.com
