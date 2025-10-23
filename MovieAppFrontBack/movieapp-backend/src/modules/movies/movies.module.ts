// module movies completo con tutte le dipendenze

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { HttpModule } from '@nestjs/axios';
import { ConfigModule } from '@nestjs/config';
import { MoviesController } from './movies.controller';
import { MoviesService } from './movies.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { MovieEntity } from '../../database/entities/movie.entity';
import { TmdbCacheEntity } from '../../database/entities/tmdb-cache.entity';
import { DatabaseModule } from '../../database/database.module';
import { WebsocketModule } from '../websocket/websocket.module';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { UserMoviesService } from './user-movies.service';
import { DatabaseService } from '../../database/database.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([MovieEntity, TmdbCacheEntity, UserMovieEntity]),
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),
    ConfigModule,
    DatabaseModule,
    WebsocketModule,
  ],
  controllers: [MoviesController],
  providers: [MoviesService, DatabaseService, TmdbService, WebsocketGateway, UserMoviesService],
  exports: [MoviesService, UserMoviesService],
})
export class MoviesModule {}