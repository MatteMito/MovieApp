// FILE: movieapp-backend/src/modules/tmdb/tmdb.module.ts
// Modulo aggiornato con controller

import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { TmdbService } from './tmdb.service';
import { TmdbController } from './tmdb.controller'; 
import { DatabaseModule } from '../../database/database.module';

@Module({
  imports: [
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
  controllers: [TmdbController],
  providers: [TmdbService],
  exports: [TmdbService],
})
export class TmdbModule {}