// modulo analytics con accesso a movies e user_movies

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AnalyticsController } from './analytics.controller';
import { AnalyticsService } from './analytics.service';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';

@Module({
  imports: [
    // registra le entity necessarie per le query analytics
    TypeOrmModule.forFeature([
      MovieEntity, // dati film (metadati tmdb)
      UserMovieEntity, // dati utente-film (rating, status, watched_date)
    ]),
  ],
  controllers: [AnalyticsController],
  providers: [AnalyticsService],
  exports: [AnalyticsService],
})
export class AnalyticsModule {}