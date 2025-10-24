// FILE: src/modules/lists/lists.module.ts
// Module per gestione liste

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ListsController } from './lists.controller';
import { ListsService } from './lists.service';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      MovieListEntity,
      MovieEntity,
    ]),
  ],
  controllers: [ListsController],
  providers: [ListsService],
  exports: [ListsService],
})
export class ListsModule {}