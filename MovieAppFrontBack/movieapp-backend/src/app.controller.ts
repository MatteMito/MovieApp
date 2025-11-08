// controller principale con endpoint root per verificare stato del server
// fornisce informazioni su api disponibili e configurazione sistema

import { Controller, Get } from '@nestjs/common';

@Controller()
export class AppController {
  /**
   * endpoint: GET /
   * descrizione: verifica che il server sia attivo e funzionante
   * risposta: informazioni base sul backend e endpoint disponibili
   */
  @Get()
  getRoot() {
    return {
      message: 'MovieApp Backend v1.0 Attivo',
      version: '1.0.0',
      timestamp: new Date().toISOString(),
      
      // lista degli endpoint principali disponibili
      endpoints: {
        api: '/api/v1',
        health: '/api/v1/movies/health',
        enrich: '/api/v1/movies/enrich',
        analytics: '/api/v1/analytics',
        lists: '/api/v1/lists',
        auth: '/api/v1/auth',
      },
      
      // informazioni sulla configurazione
      database: 'PostgreSQL',
      cache: 'TMDB Cache',
      websocket: 'Real-time notifications',
      status: 'running',
    };
  }

  /**
   * endpoint: GET /api/v1
   * descrizione: documentazione completa degli endpoint api disponibili
   * risposta: lista dettagliata di tutti gli endpoint rest e websocket
   */
  @Get('api/v1')
  getApiInfo() {
    return {
      message: 'MovieApp API v1.0',
      version: '1.0.0',
      timestamp: new Date().toISOString(),
      
      // documentazione completa endpoint suddivisi per modulo
      availableEndpoints: {
        // endpoint modulo movies
        movies: {
          health: 'GET /api/v1/movies/health',
          enrich: 'POST /api/v1/movies/enrich',
          batch: 'POST /api/v1/movies/batch',
          all: 'GET /api/v1/movies/all',
          search: 'GET /api/v1/movies/search',
          initialize: 'GET /api/v1/movies/initialize',
        },
        
        // endpoint modulo analytics
        analytics: {
          generate: 'POST /api/v1/analytics/generate',
          cached: 'GET /api/v1/analytics/cached',
          charts: 'POST /api/v1/analytics/charts/all',
          quickStats: 'POST /api/v1/analytics/quick-stats',
        },
        
        // endpoint modulo lists
        lists: {
          create: 'POST /api/v1/lists',
          getUserLists: 'GET /api/v1/lists',
          getById: 'GET /api/v1/lists/:id',
          update: 'PUT /api/v1/lists/:id',
          delete: 'DELETE /api/v1/lists/:id',
          addMovie: 'POST /api/v1/lists/:id/movies',
          removeMovie: 'DELETE /api/v1/lists/:id/movies/:movieId',
          public: 'GET /api/v1/lists/public',
          follow: 'POST /api/v1/lists/:id/follow',
        },
        
        // endpoint modulo auth
        auth: {
          register: 'POST /api/v1/auth/register',
          login: 'POST /api/v1/auth/login',
          profile: 'GET /api/v1/auth/profile',
          validate: 'GET /api/v1/auth/validate',
        },
      },
      
      // descrizione generale dell'api
      documentation: 'Tutti gli endpoint sono attivi e funzionanti',
      
      // informazioni database
      database: {
        type: 'postgresql',
        orm: 'typeorm',
        entities: ['users', 'movies', 'movie_lists', 'user_movies'],
        status: 'active',
      },
      
      // informazioni cache
      cache: {
        tmdb: 'persistent',
        analytics: 'in-memory',
        performance: 'optimized',
      },
      
      // informazioni websocket
      websocket: {
        namespace: '/ws',
        features: ['enrichment-notifications', 'real-time-updates'],
      },
    };
  }
}