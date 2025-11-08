// controller principale con endpoint root e info api

import { Controller, Get } from '@nestjs/common';

@Controller()
export class AppController {
  // GET /
  // endpoint root per verificare che il server funzioni
  @Get()
  getRoot() {
    return {
      message: 'MovieApp Backend v1.0 Attivo',
      version: '1.0.0',
      timestamp: new Date().toISOString(),
      endpoints: {
        api: '/api/v1',
        health: '/api/v1/movies/health',
        enrich: '/api/v1/movies/enrich',
        analytics: '/api/v1/analytics',
        lists: '/api/v1/lists',
        auth: '/api/v1/auth',
      },
      database: 'PostgreSQL',
      cache: 'TMDB Cache',
      websocket: 'Real-time notifications',
      status: 'running',
    };
  }

  // GET /api/v1
  // informazioni complete su tutti gli endpoint disponibili
  @Get('api/v1')
  getApiInfo() {
    return {
      message: 'MovieApp API v1.0',
      version: '1.0.0',
      timestamp: new Date().toISOString(),
      availableEndpoints: {
        movies: {
          health: 'GET /api/v1/movies/health', // health check sistema
          enrich: 'POST /api/v1/movies/enrich', // arricchisci film con tmdb
          batch: 'POST /api/v1/movies/batch', // batch upload watchlist+watched
          all: 'GET /api/v1/movies/all', // tutti i film (deprecato)
          search: 'GET /api/v1/movies/search', // ricerca film
          initialize: 'GET /api/v1/movies/initialize', // init primo avvio
        },
        analytics: {
          generate: 'POST /api/v1/analytics/generate', // genera statistiche
          cached: 'GET /api/v1/analytics/cached', // recupera cache
          charts: 'POST /api/v1/analytics/charts/all', // dati charts
          quickStats: 'POST /api/v1/analytics/quick-stats', // stats veloci
        },
        lists: {
          create: 'POST /api/v1/lists', // crea lista
          getUserLists: 'GET /api/v1/lists', // liste utente
          getById: 'GET /api/v1/lists/:id', // dettagli lista
          update: 'PUT /api/v1/lists/:id', // aggiorna lista
          delete: 'DELETE /api/v1/lists/:id', // elimina lista
          addMovie: 'POST /api/v1/lists/:id/movies', // aggiungi film
          removeMovie: 'DELETE /api/v1/lists/:id/movies/:movieId', // rimuovi film
          public: 'GET /api/v1/lists/public', // liste pubbliche
          follow: 'POST /api/v1/lists/:id/follow', // segui lista
        },
        auth: {
          register: 'POST /api/v1/auth/register', // registrazione
          login: 'POST /api/v1/auth/login', // login
          profile: 'GET /api/v1/auth/profile', // profilo utente
          validate: 'GET /api/v1/auth/validate', // valida token
        },
      },
      documentation: 'Tutti gli endpoint sono attivi e funzionanti',
      database: {
        type: 'postgresql',
        orm: 'typeorm',
        entities: ['users', 'movies', 'movie_lists', 'user_movies'],
        status: 'active',
      },
      cache: {
        tmdb: 'persistent', // cache tmdb in movies table
        analytics: 'in-memory', // cache analytics in map
        performance: 'optimized',
      },
      websocket: {
        namespace: '/ws', // endpoint websocket
        features: ['enrichment-notifications', 'real-time-updates'],
      },
    };
  }
}