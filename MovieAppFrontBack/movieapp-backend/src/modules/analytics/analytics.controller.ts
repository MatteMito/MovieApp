//controller analytics con endpoints statistiche

import {
  Controller,
  Get,
  HttpException,
  HttpStatus,
  Logger,
  Query,
} from '@nestjs/common';
import { AnalyticsService } from './analytics.service';

@Controller('api/v1/analytics')
export class AnalyticsController {
  private readonly logger = new Logger(AnalyticsController.name);

  constructor(private readonly analyticsService: AnalyticsService) {}

  /**
   * GET /api/v1/analytics/basic
   * statistiche base
   */
  @Get('basic')
  async getBasicStats() {
    try {
      this.logger.log('richiesta stats base');

      const stats = await this.analyticsService.getBasicStats();

      return {
        success: true,
        data: stats,
        message: 'statistiche base recuperate',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats base: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero statistiche',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/genres
   * statistiche generi
   */
  @Get('genres')
  async getGenreStats(@Query('limit') limit?: string) {
    try {
      const limitNum = limit ? parseInt(limit) : 10;
      this.logger.log(`richiesta stats generi (limit: ${limitNum})`);

      const stats = await this.analyticsService.getGenreStats(limitNum);

      return {
        success: true,
        data: stats,
        message: `top ${stats.length} generi recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats generi: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero statistiche generi',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/years
   * statistiche anni
   */
  @Get('years')
  async getYearStats() {
    try {
      this.logger.log('richiesta stats anni');

      const stats = await this.analyticsService.getYearStats();

      return {
        success: true,
        data: stats,
        message: `statistiche ${stats.length} anni recuperate`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats anni: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero statistiche anni',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/directors
   * statistiche registi
   */
  @Get('directors')
  async getDirectorStats(@Query('limit') limit?: string) {
    try {
      const limitNum = limit ? parseInt(limit) : 10;
      this.logger.log(`richiesta stats registi (limit: ${limitNum})`);

      const stats = await this.analyticsService.getDirectorStats(limitNum);

      return {
        success: true,
        data: stats,
        message: `top ${stats.length} registi recuperati`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore stats registi: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero statistiche registi',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/rating-distribution
   * distribuzione rating
   */
  @Get('rating-distribution')
  async getRatingDistribution() {
    try {
      this.logger.log('richiesta distribuzione rating');

      const distribution =
        await this.analyticsService.getRatingDistribution();

      return {
        success: true,
        data: distribution,
        message: 'distribuzione rating recuperata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore distribuzione rating: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero distribuzione rating',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/decade-distribution
   * distribuzione decadi
   */
  @Get('decade-distribution')
  async getDecadeDistribution() {
    try {
      this.logger.log('richiesta distribuzione decadi');

      const distribution =
        await this.analyticsService.getDecadeDistribution();

      return {
        success: true,
        data: distribution,
        message: 'distribuzione decadi recuperata',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore distribuzione decadi: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero distribuzione decadi',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/advanced
   * analytics avanzate complete
   */
  @Get('advanced')
  async getAdvancedAnalytics() {
    try {
      this.logger.log('richiesta analytics avanzate');

      const analytics = await this.analyticsService.getAdvancedAnalytics();

      return {
        success: true,
        data: analytics,
        message: 'analytics avanzate recuperate',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore analytics avanzate: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore recupero analytics avanzate',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * GET /api/v1/analytics/report
   * report testuale completo
   */
  @Get('report')
  async getTextReport() {
    try {
      this.logger.log('richiesta report testuale');

      const report = await this.analyticsService.generateTextReport();

      return {
        success: true,
        data: { report },
        message: 'report generato',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore report: ${error.message}`);

      throw new HttpException(
        {
          success: false,
          message: 'errore generazione report',
          timestamp: new Date().toISOString(),
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }
}