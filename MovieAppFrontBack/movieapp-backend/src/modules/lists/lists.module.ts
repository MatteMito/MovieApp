// module per gestione liste personalizzate

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ListsController } from './lists.controller';
import { ListsService } from './lists.service';
import { MovieListEntity } from '../../database/entities/list.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';

@Module({
  imports: [
    // registra le entity necessarie per le operazioni sulle liste
    TypeOrmModule.forFeature([
      MovieListEntity, // liste personalizzate
      MovieEntity, // film da aggiungere alle liste
      UserEntity, // proprietari e follower delle liste
    ]),
  ],
  controllers: [ListsController],
  providers: [ListsService],
  exports: [ListsService],
})
export class ListsModule {}