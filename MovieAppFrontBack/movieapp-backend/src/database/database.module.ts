// File: src/database/database.module.ts

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DatabaseService } from './database.service';
import { MovieEntity } from './entities/movie.entity';
import { UserEntity } from './entities/user.entity';
import { TmdbCacheEntity } from './entities/tmdb-cache.entity';
import { UserMovieEntity } from './entities/user-movie.entity'; // ⬅️ AGGIUNTO

@Module({
  imports: [
    TypeOrmModule.forFeature([
      MovieEntity,
      UserEntity,
      TmdbCacheEntity,
      UserMovieEntity, // ⬅️ AGGIUNTO
    ]),
  ],
  providers: [DatabaseService],
  exports: [DatabaseService, TypeOrmModule],
})
export class DatabaseModule {}