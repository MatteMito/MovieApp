// File: src/modules/analytics/analytics.controller.ts
// AGGIORNATO: tutti i metodi richiedono userId

import {
  Controller,
  Get,
  Query,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { AnalyticsService } from './analytics.service';

interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  timestamp: string;
}

@Controller('api/v1/analytics')
export class AnalyticsController {
  private readonly logger = new Logger(AnalyticsController.name);

  constructor(private readonly analyticsService: AnalyticsService) {}

  /**
   * GET /api/v1/analytics/basic?userId=xxx
   */
  @Get('basic')
  async getBasicStats(@Query('userId') userId: string): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`📊 statistiche base richieste per utente ${userId}`);
      const stats = await this.analyticsService.getBasicStats(userId);

      return {
        success: true,
        data: stats,
        message: 'statistiche base recuperate',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore statistiche base: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/genres?userId=xxx&limit=10
   */
  @Get('genres')
  async getGenreStats(
    @Query('userId') userId: string,
    @Query('limit') limit?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      const limitNum = limit ? parseInt(limit, 10) : 10;
      this.logger.log(`📊 statistiche generi per utente ${userId}`);
      
      const stats = await this.analyticsService.getGenreStats(userId);
      const limitedStats = stats.slice(0, limitNum);

      return {
        success: true,
        data: limitedStats,
        message: `top ${limitedStats.length} generi`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore statistiche generi: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/years?userId=xxx
   */
  @Get('years')
  async getYearStats(@Query('userId') userId: string): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`📊 statistiche anni per utente ${userId}`);
      const stats = await this.analyticsService.getYearStats(userId);

      return {
        success: true,
        data: stats,
        message: 'distribuzione per anno',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore statistiche anni: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/directors?userId=xxx&limit=10
   */
  @Get('directors')
  async getDirectorStats(
    @Query('userId') userId: string,
    @Query('limit') limit?: string,
  ): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      const limitNum = limit ? parseInt(limit, 10) : 10;
      this.logger.log(`📊 statistiche registi per utente ${userId}`);
      
      const stats = await this.analyticsService.getDirectorStats(userId);
      const limitedStats = stats.slice(0, limitNum);

      return {
        success: true,
        data: limitedStats,
        message: `top ${limitedStats.length} registi`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore statistiche registi: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/advanced?userId=xxx
   */
  @Get('advanced')
  async getAdvancedAnalytics(@Query('userId') userId: string): Promise<ApiResponse> {
    try {
      if (!userId) {
        throw new HttpException(
          { success: false, message: 'userId mancante', timestamp: new Date().toISOString() },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`📊 analytics avanzate per utente ${userId}`);
      const analytics = await this.analyticsService.getAdvancedAnalytics(userId);

      return {
        success: true,
        data: analytics,
        message: 'analytics complete',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore analytics avanzate: ${error.message}`);
      throw new HttpException(
        { success: false, message: error.message, timestamp: new Date().toISOString() },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}