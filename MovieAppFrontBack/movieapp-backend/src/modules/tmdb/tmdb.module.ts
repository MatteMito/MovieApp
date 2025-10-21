// Modulo per integrazione TMDB API

import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { TmdbService } from './tmdb.service';
import { DatabaseModule } from '../../database/database.module';

@Module({
  imports: [
    // HTTP Module per chiamate API TMDB
    HttpModule.register({
      timeout: 10000,
      maxRedirects: 5,
      headers: {
        Accept: 'application/json',
        'User-Agent': 'MovieApp/2.1 con Database PostgreSQL',
      },
    }),
    DatabaseModule,
  ],
  providers: [TmdbService],
  exports: [TmdbService],
})
export class TmdbModule {}