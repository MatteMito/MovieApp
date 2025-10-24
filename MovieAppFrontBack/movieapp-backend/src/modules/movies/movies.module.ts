// File: src/modules/movies/movies.module.ts

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { MoviesController } from './movies.controller';
import { MoviesService } from './movies.service';
import { UserMoviesService } from './user-movies.service'; // ⬅️ AGGIUNTO
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity'; // ⬅️ AGGIUNTO
import { TmdbCacheEntity } from '../../database/entities/tmdb-cache.entity';
import { DatabaseModule } from '../../database/database.module';
import { TmdbModule } from '../tmdb/tmdb.module';
import { WebsocketModule } from '../websocket/websocket.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      MovieEntity,
      UserMovieEntity, // ⬅️ AGGIUNTO
      TmdbCacheEntity,
    ]),
    DatabaseModule,
    TmdbModule,
    WebsocketModule,
  ],
  controllers: [MoviesController],
  providers: [
    MoviesService,
    UserMoviesService, // ⬅️ AGGIUNTO
  ],
  exports: [
    MoviesService,
    UserMoviesService, // ⬅️ AGGIUNTO
  ],
})
export class MoviesModule {}