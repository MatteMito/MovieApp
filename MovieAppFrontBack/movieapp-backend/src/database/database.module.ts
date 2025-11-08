import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DatabaseService } from './database.service';
import { MovieEntity } from './entities/movie.entity';
import { UserEntity } from './entities/user.entity';
import { UserMovieEntity } from './entities/user-movie.entity';

@Module({
  imports: [
    // registra le entity da usare in questo modulo
    TypeOrmModule.forFeature([
      MovieEntity, // catalogo film
      UserEntity, // utenti
      UserMovieEntity, // relazione utente-film con dati personali
    ]),
  ],
  providers: [DatabaseService],
  exports: [DatabaseService, TypeOrmModule],
})
export class DatabaseModule {}