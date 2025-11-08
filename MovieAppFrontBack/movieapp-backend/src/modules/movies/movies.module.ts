// ottimizzato: rimosso tmdb cache entity (non necessaria)

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { MoviesController } from './movies.controller';
import { MoviesService } from './movies.service';
import { UserMoviesService } from './user-movies.service';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { DatabaseModule } from '../../database/database.module';
import { TmdbModule } from '../tmdb/tmdb.module';
import { WebsocketModule } from '../websocket/websocket.module';

@Module({
  imports: [
    // registra entity per accesso database
    TypeOrmModule.forFeature([
      MovieEntity, // catalogo film con metadati tmdb
      UserMovieEntity, // relazione utente-film con rating e status
    ]),
    DatabaseModule, // service database per operazioni custom
    TmdbModule, // service per enrichment film via tmdb api
    WebsocketModule, // websocket per notifiche real-time (progress enrichment)
  ],
  controllers: [MoviesController],
  providers: [
    MoviesService,
    UserMoviesService,
  ],
  exports: [
    MoviesService,
    UserMoviesService,
  ],
})
export class MoviesModule {}