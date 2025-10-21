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

@Module({
  imports: [
    TypeOrmModule.forFeature([MovieEntity, TmdbCacheEntity]),
    HttpModule.register({
      timeout: 30000,
      maxRedirects: 5,
    }),
    ConfigModule,
    DatabaseModule,
    WebsocketModule,
  ],
  controllers: [MoviesController],
  providers: [MoviesService, TmdbService],
  exports: [MoviesService, TmdbService],
})
export class MoviesModule {}