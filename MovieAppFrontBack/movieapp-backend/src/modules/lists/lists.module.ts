// File: src/modules/lists/lists.module.ts

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ListsController } from './lists.controller';
import { ListsService } from './lists.service';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity'; // ⬅️ DEVE ESSERCI

@Module({
  imports: [
    TypeOrmModule.forFeature([
      MovieEntity,
      UserMovieEntity, // ⬅️ DEVE ESSERCI
    ]),
  ],
  controllers: [ListsController],
  providers: [ListsService],
  exports: [ListsService],
})
export class ListsModule {}