//module database con servizi postgresql

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ConfigModule } from '@nestjs/config';
import { DatabaseService } from './database.service';
import { MovieEntity } from './entities/movie.entity';
import { UserEntity } from './entities/user.entity';
import { TmdbCacheEntity } from './entities/tmdb-cache.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([MovieEntity, UserEntity, TmdbCacheEntity]),
    ConfigModule,
  ],
  providers: [DatabaseService],
  exports: [DatabaseService, TypeOrmModule],
})
export class DatabaseModule {}