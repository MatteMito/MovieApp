// modulo gestione film con enrichment tmdb e associazioni utente
// coordina movies service, user-movies service, database, tmdb, websocket

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
    // registra entita per accesso repository
    TypeOrmModule.forFeature([
      MovieEntity,
      UserMovieEntity,
    ]),
    
    // importa moduli dipendenti
    DatabaseModule,
    TmdbModule,
    WebsocketModule,
  ],
  
  // controller per endpoint rest api
  controllers: [MoviesController],
  
  // service con logica business
  providers: [
    MoviesService,
    UserMoviesService,
  ],
  
  // esporta service per essere usati in altri moduli
  exports: [
    MoviesService,
    UserMoviesService,
  ],
})
export class MoviesModule {}