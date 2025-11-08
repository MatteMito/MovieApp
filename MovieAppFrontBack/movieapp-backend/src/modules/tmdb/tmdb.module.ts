// modulo integrazione api the movie database (tmdb)
// gestisce ricerca film, enrichment dati, autocomplete, sync database

import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { TmdbService } from './tmdb.service';
import { TmdbController } from './tmdb.controller';
import { TmdbScheduler } from './tmdb.scheduler';
import { DatabaseModule } from '../../database/database.module';

@Module({
  imports: [
    // configura http module per chiamate api tmdb
    HttpModule.register({
      timeout: 10000, // timeout 10 secondi per richieste http
      maxRedirects: 5,
      headers: {
        Accept: 'application/json',
        'User-Agent': 'MovieApp/2.1 con Database PostgreSQL',
      },
    }),
    
    // importa database module per salvare dati tmdb
    DatabaseModule,
  ],
  
  // controller per endpoint rest api
  controllers: [TmdbController],
  
  // service con logica business + scheduler per task periodici
  providers: [
    TmdbService,
    TmdbScheduler,
  ],
  
  // esporta tmdb service per essere usato in movies module
  exports: [TmdbService],
})
export class TmdbModule {}