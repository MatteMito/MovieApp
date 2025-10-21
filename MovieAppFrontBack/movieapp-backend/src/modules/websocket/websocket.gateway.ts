//websocket gateway per notifiche real-time ottimizzato

import {
  WebSocketGateway,
  WebSocketServer,
  OnGatewayInit,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Logger } from '@nestjs/common';
import { Server, Socket } from 'socket.io';

//interfacce messaggi websocket
interface EnrichmentProgressMessage {
  sessionId: string;
  type: 'progress' | 'completed' | 'error' | 'started';
  total?: number;
  processed?: number;
  successful?: number;
  failed?: number;
  currentMovie?: string;
  message: string;
  percentage?: number;
  error?: string;
}

interface ChartNotificationMessage {
  type: string;
  status: 'generating' | 'generated' | 'error';
  dataPoints?: number;
  message: string;
  error?: string;
}

@WebSocketGateway({
  cors: {
    origin: '*',
    credentials: true,
  },
  namespace: '/ws',
})
export class WebsocketGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server: Server;

  private readonly logger = new Logger(WebsocketGateway.name);
  private connectedClients: Set<string> = new Set();
  private activeEnrichmentSessions: Map<string, any> = new Map();

  //lifecycle hooks

  afterInit(server: Server) {
    this.logger.log('✅ websocket gateway inizializzato');
    this.logger.log(`namespace: /ws`);
  }

  handleConnection(client: Socket) {
    this.connectedClients.add(client.id);
    this.logger.log(`✅ client connesso: ${client.id} (totale: ${this.connectedClients.size})`);

    //invia messaggio benvenuto
    client.emit('connection', {
      type: 'connected',
      message: 'connesso al backend movieapp v2.0',
      clientId: client.id,
      features: [
        'real-time enrichment notifications',
        'chart generation updates',
        'system status alerts',
      ],
      timestamp: new Date().toISOString(),
    });
  }

  handleDisconnect(client: Socket) {
    this.connectedClients.delete(client.id);
    this.logger.log(`❌ client disconnesso: ${client.id} (totale: ${this.connectedClients.size})`);
  }

  //NOTIFICHE ENRICHMENT

  async notifyEnrichmentProgress(
    sessionId: string,
    processed: number,
    total: number,
    currentMovie?: string,
  ): Promise<void> {
    try {
      const percentage = total > 0 ? Math.round((processed / total) * 100) : 0;

      const message: EnrichmentProgressMessage = {
        sessionId,
        type: 'progress',
        total,
        processed,
        currentMovie,
        message: `elaborazione: ${processed}/${total} film`,
        percentage,
      };

      this.activeEnrichmentSessions.set(sessionId, {
        ...message,
        lastUpdate: new Date().toISOString(),
      });

      this.server.emit('enrichment:progress', message);

      this.logger.debug(
        `📊 progress notificato: ${processed}/${total} (${percentage}%) - ${currentMovie || 'N/A'}`,
      );
    } catch (error) {
      this.logger.error(`errore notifica progress: ${error.message}`);
    }
  }

  async notifyEnrichmentCompleted(
    sessionId: string,
    total: number,
    successful: number,
    cacheHits: number = 0,
  ): Promise<void> {
    try {
      const backendProcessed = successful - cacheHits;
      const successRate = total > 0 ? Math.round((successful / total) * 100) : 0;

      const message: EnrichmentProgressMessage = {
        sessionId,
        type: 'completed',
        total,
        processed: total,
        successful,
        failed: total - successful,
        message: `✅ enrichment completato!\n` +
                 `📊 ${successful}/${total} film elaborati (${successRate}%)\n` +
                 `💾 cache hits: ${cacheHits}\n` +
                 `🔄 nuovi da tmdb: ${backendProcessed}`,
        percentage: 100,
      };

      this.activeEnrichmentSessions.set(sessionId, {
        ...message,
        endTime: new Date().toISOString(),
        status: 'completed',
      });

      this.server.emit('enrichment:completed', message);

      this.logger.log(
        `✅ enrichment completato: ${successful}/${total} (cache: ${cacheHits}, tmdb: ${backendProcessed})`,
      );

      //notifica aggiornamento grafici dopo enrichment
      setTimeout(() => {
        this.notifyChartUpdate('all', successful);
      }, 1000);
    } catch (error) {
      this.logger.error(`errore notifica completed: ${error.message}`);
    }
  }

  async notifyEnrichmentError(sessionId: string, error: string): Promise<void> {
    try {
      const message: EnrichmentProgressMessage = {
        sessionId,
        type: 'error',
        message: `❌ errore enrichment: ${error}`,
        error,
        percentage: 0,
      };

      this.activeEnrichmentSessions.set(sessionId, {
        ...message,
        status: 'error',
        timestamp: new Date().toISOString(),
      });

      this.server.emit('enrichment:error', message);

      this.logger.error(`❌ enrichment error notificato: ${error}`);
    } catch (err) {
      this.logger.error(`errore notifica error: ${err.message}`);
    }
  }

  //NOTIFICHE BATCH

  async notifyBatchCompleted(
    sessionId: string,
    watchlistCount: number,
    watchedCount: number,
    total: number,
  ): Promise<void> {
    try {
      const message = {
        sessionId,
        type: 'batch_completed',
        watchlistCount,
        watchedCount,
        total,
        message: `✅ batch completato!\n` +
                 `📊 ${total} film importati\n` +
                 `• watchlist: ${watchlistCount}\n` +
                 `• watched: ${watchedCount}`,
        timestamp: new Date().toISOString(),
      };

      this.server.emit('batch:completed', message);

      this.logger.log(`✅ batch completato: ${total} film (${watchlistCount} + ${watchedCount})`);
    } catch (error) {
      this.logger.error(`errore notifica batch: ${error.message}`);
    }
  }

  //NOTIFICHE GRAFICI

  async notifyChartUpdate(chartType: string, dataPoints: number): Promise<void> {
    try {
      const message: ChartNotificationMessage = {
        type: chartType,
        status: 'generated',
        dataPoints,
        message: `grafico ${chartType} generato con ${dataPoints} elementi`,
      };

      this.server.emit('chart:update', message);

      this.logger.debug(`📈 grafico aggiornato: ${chartType} (${dataPoints} punti)`);
    } catch (error) {
      this.logger.error(`errore notifica chart: ${error.message}`);
    }
  }

  async notifyChartError(chartType: string, error: string): Promise<void> {
    try {
      const message: ChartNotificationMessage = {
        type: chartType,
        status: 'error',
        message: `errore grafico ${chartType}: ${error}`,
        error,
      };

      this.server.emit('chart:error', message);

      this.logger.error(`❌ errore grafico ${chartType}: ${error}`);
    } catch (err) {
      this.logger.error(`errore notifica chart error: ${err.message}`);
    }
  }

  //NOTIFICHE SISTEMA

  async notifySystem(type: string, message: string, data?: any): Promise<void> {
    try {
      const notification = {
        type,
        message,
        data,
        timestamp: new Date().toISOString(),
      };

      this.server.emit('system:notification', notification);

      this.logger.debug(`🔔 notifica sistema: ${type} - ${message}`);
    } catch (error) {
      this.logger.error(`errore notifica sistema: ${error.message}`);
    }
  }

  //GESTIONE SESSIONI

  getEnrichmentSession(sessionId: string): any | null {
    return this.activeEnrichmentSessions.get(sessionId) || null;
  }

  /**
   * pulisce sessioni completate o scadute
   */
  cleanExpiredSessions(): void {
    try {
      const now = Date.now();
      const maxAge = 3600000; //1 ora

      for (const [sessionId, session] of this.activeEnrichmentSessions) {
        const sessionTime = session.endTime || session.lastUpdate || session.startTime;
        const sessionAge = now - new Date(sessionTime).getTime();

        if (sessionAge > maxAge) {
          this.activeEnrichmentSessions.delete(sessionId);
          this.logger.debug(`🧹 sessione scaduta rimossa: ${sessionId}`);
        }
      }
    } catch (error) {
      this.logger.error(`errore pulizia sessioni: ${error.message}`);
    }
  }

  //UTILITY

  /**
   * info connessioni attive
   */
  getConnectionInfo(): any {
    return {
      status: 'active',
      connectedClients: this.connectedClients.size,
      activeSessions: this.activeEnrichmentSessions.size,
      namespace: '/ws',
      features: [
        'enrichment notifications',
        'chart updates',
        'system alerts',
      ],
    };
  }

  /**
   * broadcast messaggio a tutti i client
   */
  broadcastMessage(event: string, data: any): void {
    try {
      this.server.emit(event, data);
      this.logger.debug(`📡 broadcast: ${event}`);
    } catch (error) {
      this.logger.error(`errore broadcast: ${error.message}`);
    }
  }
}