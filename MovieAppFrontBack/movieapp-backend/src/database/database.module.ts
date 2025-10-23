//module database con servizi postgresql

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ConfigModule } from '@nestjs/config';
import { DatabaseService } from './database.service';
import { MovieEntity } from './entities/movie.entity';
import { UserMovieEntity } from './entities/user-movie.entity';
import { TmdbCacheEntity } from './entities/tmdb-cache.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([MovieEntity, TmdbCacheEntity, UserMovieEntity]),
    ConfigModule,
  ],
  providers: [DatabaseService],
  exports: [DatabaseService, TypeOrmModule],
})
export class DatabaseModule {}