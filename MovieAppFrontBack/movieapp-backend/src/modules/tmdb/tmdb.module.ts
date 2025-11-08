// module per integrazione tmdb api

import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { TmdbService } from './tmdb.service';
import { TmdbController } from './tmdb.controller';
import { TmdbScheduler } from './tmdb.scheduler';
import { DatabaseModule } from '../../database/database.module';

@Module({
  imports: [
    // configura http client per chiamate api tmdb
    HttpModule.register({
      timeout: 10000, // timeout 10 secondi
      maxRedirects: 5, // segue max 5 redirect
      headers: {
        Accept: 'application/json',
        'User-Agent': 'MovieApp/1.0 con Database PostgreSQL',
      },
    }),
    DatabaseModule, // per salvare film arricchiti nel db
  ],
  controllers: [TmdbController],
  providers: [
    TmdbService, // business logic enrichment e ricerca tmdb
    TmdbScheduler, // task schedulati (sync periodico)
  ],
  exports: [TmdbService],
})
export class TmdbModule {}