//module analytics per statistiche avanzate

import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AnalyticsController } from './analytics.controller';
import { AnalyticsService } from './analytics.service';
import { MovieEntity } from '../../database/entities/movie.entity';
import { TmdbCacheEntity } from '../../database/entities/tmdb-cache.entity';
import { DatabaseModule } from '../../database/database.module';

@Module({
  imports: [
    TypeOrmModule.forFeature([MovieEntity, TmdbCacheEntity]),
    DatabaseModule,
  ],
  controllers: [AnalyticsController],
  providers: [AnalyticsService],
  exports: [AnalyticsService],
})
export class AnalyticsModule {}