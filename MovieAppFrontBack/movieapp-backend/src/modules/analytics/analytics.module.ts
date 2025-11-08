// modulo analytics per generazione statistiche avanzate
// calcola grafici per visualizzazione dati in app android

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AnalyticsController } from './analytics.controller';
import { AnalyticsService } from './analytics.service';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';

@Module({
  imports: [
    // registra entita per accesso query analytics
    TypeOrmModule.forFeature([
      MovieEntity,
      UserMovieEntity,
    ]),
  ],
  
  controllers: [AnalyticsController],
  
  providers: [AnalyticsService],
  
  exports: [AnalyticsService],
})
export class AnalyticsModule {}