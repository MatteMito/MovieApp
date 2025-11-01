import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { TmdbService } from './tmdb.service';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class TmdbScheduler {
  private readonly logger = new Logger(TmdbScheduler.name);
  private readonly enableAutoSync: boolean;

  constructor(
    private readonly tmdbService: TmdbService,
    private readonly configService: ConfigService,
  ) {
    //leggi da env se abilitare auto sync
    this.enableAutoSync = 
      this.configService.get<string>('ENABLE_AUTO_SYNC') === 'true';

    if (this.enableAutoSync) {
      this.logger.log('✅ auto sync tmdb abilitato');
    } else {
      this.logger.log('⚠️ auto sync tmdb disabilitato (abilita con ENABLE_AUTO_SYNC=true)');
    }
  }

  /**
   * sync completo: ogni 1° del mese alle 03:00
   * scarica top 10.000 film popolari
   */
  @Cron('0 3 1 * *', {
    name: 'sync-popular-full',
    timeZone: 'Europe/Rome',
  })
  async syncPopularMoviesFull() {
    if (!this.enableAutoSync) {
      return;
    }

    this.logger.log('🔄 avvio sync completo mensile (10.000 film)');

    try {
      const result = await this.tmdbService.syncPopularMovies(10000);

      this.logger.log('✅ sync completo completato:');
      this.logger.log(`   sincronizzati: ${result.synced}`);
      this.logger.log(`   errori: ${result.errors}`);
    } catch (error) {
      this.logger.error(`❌ errore sync completo: ${error.message}`);
    }
  }

  /**
   * sync incrementale: ogni lunedì alle 02:00
   * scarica top 500 film per aggiornamenti
   */
  @Cron('0 2 * * 1', {
    name: 'sync-popular-incremental',
    timeZone: 'Europe/Rome',
  })
  async syncPopularMoviesIncremental() {
    if (!this.enableAutoSync) {
      return;
    }

    this.logger.log('🔄 avvio sync incrementale settimanale (500 film)');

    try {
      const result = await this.tmdbService.syncPopularMovies(500);

      this.logger.log('✅ sync incrementale completato:');
      this.logger.log(`   sincronizzati: ${result.synced}`);
      this.logger.log(`   errori: ${result.errors}`);
    } catch (error) {
      this.logger.error(`❌ errore sync incrementale: ${error.message}`);
    }
  }

  /**
   * pulizia cache: ogni domenica alle 04:00
   * rimuove film non arricchiti e non usati da 6+ mesi
   */
  @Cron('0 4 * * 0', {
    name: 'cleanup-unused-movies',
    timeZone: 'Europe/Rome',
  })
  async cleanupUnusedMovies() {
    if (!this.enableAutoSync) {
      return;
    }

    this.logger.log('🧹 avvio pulizia film non usati');

    //todo: implementa logica pulizia
    //rimuovi film con is_enriched=false e created_at < 6 mesi fa
    //che non sono associati a nessun utente

    this.logger.log('⚠️ pulizia non ancora implementata');
  }

  /**
   * metodo manuale per testare
   */
  async testSync() {
    this.logger.log('🧪 test sync manuale (10 film)');
    
    try {
      const result = await this.tmdbService.syncPopularMovies(10);
      
      this.logger.log('✅ test sync completato:');
      this.logger.log(`   sincronizzati: ${result.synced}`);
      this.logger.log(`   errori: ${result.errors}`);
      
      return result;
    } catch (error) {
      this.logger.error(`❌ errore test sync: ${error.message}`);
      throw error;
    }
  }
}