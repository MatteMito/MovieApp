// file: src/modules/tmdb/tmdb.controller.ts
// controller per ricerca film tmdb e sync

import {
  Controller,
  Get,
  Post,
  Body,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { TmdbService } from './tmdb.service';
import { DatabaseService } from '../../database/database.service';

interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
}

@Controller('api/v1/tmdb')
export class TmdbController {
  private readonly logger = new Logger(TmdbController.name);

  constructor(
    private readonly tmdbService: TmdbService,
    private readonly databaseService: DatabaseService,
  ) {}

  /**
   * POST /api/v1/tmdb/sync-popular
   * sincronizza top 10k film popolari da tmdb
   */
  @Post('sync-popular')
  async syncPopularMovies(
    @Body() body: { limit?: number },
  ): Promise<ApiResponse> {
    try {
      const limit = body.limit || 10000;

      this.logger.log(`avvio sync ${limit} film popolari...`);

      const result = await this.tmdbService.syncPopularMovies(limit);

      return {
        success: true,
        data: result,
        message: `sincronizzati ${result.synced} film (${result.errors} errori)`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore sync: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/tmdb/stats
   * statistiche sync database
   */
  @Get('stats')
  async getStats(): Promise<ApiResponse> {
    try {
      const stats = await this.databaseService.getSyncStats();

      return {
        success: true,
        data: stats,
        message: 'statistiche database',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}