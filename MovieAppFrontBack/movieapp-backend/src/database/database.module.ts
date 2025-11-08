// modulo database che gestisce l'accesso a postgresql
// registra le entita typeorm e esporta il service per crud operations

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DatabaseService } from './database.service';
import { MovieEntity } from './entities/movie.entity';
import { UserEntity } from './entities/user.entity';
import { UserMovieEntity } from './entities/user-movie.entity';

// decoratore module per nestjs
@Module({
  imports: [
    TypeOrmModule.forFeature([
      MovieEntity,
      UserEntity,
      UserMovieEntity,
    ]),
  ],
  
  // service che contiene la logica business del database
  providers: [DatabaseService],
  
  exports: [DatabaseService, TypeOrmModule],
})
export class DatabaseModule {}