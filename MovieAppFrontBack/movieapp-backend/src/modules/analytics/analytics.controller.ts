// controller per endpoint analytics con statistiche complete
// fornisce endpoint rest per recuperare grafici e statistiche

import {
  Controller,
  Get,
  Param,
  Query,
  HttpStatus,
  HttpException,
  Logger,
} from '@nestjs/common';
import { AnalyticsService } from './analytics.service';

// response standardizzata
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
   * endpoint: GET /api/v1/analytics/user/:userId
   * endpoint principale: ritorna tutte le statistiche in una chiamata
   * include: basic stats, watched stats, rating stats
   * usato da android per popolare tutti i grafici in notificationsfragment
   */
  @Get('user/:userId')
  async getCompleteAnalytics(@Param('userId') userId: string): Promise<ApiResponse> {
    try {
      // validazione userid
      if (!userId) {
        throw new HttpException(
          { 
            success: false, 
            message: 'userId mancante', 
            timestamp: new Date().toISOString() 
          },
          HttpStatus.BAD_REQUEST,
        );
      }

      this.logger.log(`analytics complete richieste per utente ${userId}`);
      
      // misura tempo esecuzione
      const startTime = Date.now();
      
      // genera tutte le statistiche
      const analytics = await this.analyticsService.getCompleteAnalytics(userId);
      
      const elapsed = Date.now() - startTime;
      this.logger.log(`analytics generate in ${elapsed}ms`);

      return {
        success: true,
        data: analytics,
        message: `analytics complete per ${analytics.basicStats.totalMovies} film (${analytics.basicStats.watchedCount} watched, ${analytics.basicStats.watchlistCount} watchlist)`,
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`errore analytics complete: ${error.message}`);
      throw new HttpException(
        { 
          success: false, 
          message: error.message, 
          timestamp: new Date().toISOString() 
        },
        HttpStatus.INTERNAL_SERVER_ERROR,
      );
    }
  }

  /**
   * endpoint: GET /api/v1/analytics/basic?userId=xxx
   * statistiche base (retrocompatibilita)
   * conteggi film, watched, watchlist, rating medio
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

      this.logger.log(`statistiche base richieste per utente ${userId}`);
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
   * endpoint: GET /api/v1/analytics/genres?userId=xxx&limit=10
   * statistiche generi (retrocompatibilita)
   * distribuzione generi con count e percentuali
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
      this.logger.log(`statistiche generi per utente ${userId}`);
      
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
   * endpoint: GET /api/v1/analytics/directors?userId=xxx&limit=10
   * statistiche registi (retrocompatibilita)
   * top registi per numero film visti
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
      this.logger.log(`statistiche registi per utente ${userId}`);
      
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
   * endpoint: GET /api/v1/analytics/advanced?userId=xxx
   * analytics avanzate (retrocompatibilita)
   * include tutte le statistiche disponibili
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

      this.logger.log(`analytics avanzate per utente ${userId}`);
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