// Controller principale con endpoint root

import { Controller, Get } from '@nestjs/common';

@Controller()
export class AppController {
  /**
   * GET /
   * Endpoint root per verificare che il server funzioni
   */
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

  /**
   * GET /api/v1
   * Informazioni API base
   */
  @Get('api/v1')
  getApiInfo() {
    return {
      message: 'MovieApp API v1.0',
      version: '1.0.0',
      timestamp: new Date().toISOString(),
      availableEndpoints: {
        movies: {
          health: 'GET /api/v1/movies/health',
          enrich: 'POST /api/v1/movies/enrich',
          batch: 'POST /api/v1/movies/batch',
          all: 'GET /api/v1/movies/all',
          search: 'GET /api/v1/movies/search',
          initialize: 'GET /api/v1/movies/initialize',
        },
        analytics: {
          generate: 'POST /api/v1/analytics/generate',
          cached: 'GET /api/v1/analytics/cached',
          charts: 'POST /api/v1/analytics/charts/all',
          quickStats: 'POST /api/v1/analytics/quick-stats',
        },
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
        auth: {
          register: 'POST /api/v1/auth/register',
          login: 'POST /api/v1/auth/login',
          profile: 'GET /api/v1/auth/profile',
          validate: 'GET /api/v1/auth/validate',
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
        tmdb: 'persistent',
        analytics: 'in-memory',
        performance: 'optimized',
      },
      websocket: {
        namespace: '/ws',
        features: ['enrichment-notifications', 'real-time-updates'],
      },
    };
  }
}