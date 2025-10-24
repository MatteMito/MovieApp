// File: src/database/database.module.ts
// ✅ OTTIMIZZATO: rimosso TmdbCacheEntity

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DatabaseService } from './database.service';
import { MovieEntity } from './entities/movie.entity';
import { UserEntity } from './entities/user.entity';
import { UserMovieEntity } from './entities/user-movie.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      MovieEntity,
      UserEntity,
      UserMovieEntity,
    ]),
  ],
  providers: [DatabaseService],
  exports: [DatabaseService, TypeOrmModule],
})
export class DatabaseModule {}