import {
  WebSocketGateway,
  WebSocketServer,
  OnGatewayInit,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Logger } from '@nestjs/common';
import { Server, Socket } from 'socket.io';

@WebSocketGateway({
  namespace: '/ws',
  cors: {
    origin: '*',
    credentials: true,
  },
})
export class WebsocketGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server: Server;

  private readonly logger = new Logger(WebsocketGateway.name);
  private connectedClients = new Map<string, Socket>();

  afterInit(server: Server) {
    this.logger.log('🔌 WebSocket Gateway inizializzato');
    this.logger.log('   Namespace: /ws');
  }

  handleConnection(client: Socket) {
    this.connectedClients.set(client.id, client);
    this.logger.log(`✅ Client connesso: ${client.id} (Total: ${this.connectedClients.size})`);

    // Invia messaggio di benvenuto
    client.emit('connection', {
      message: 'Connesso al server WebSocket',
      clientId: client.id,
      timestamp: new Date().toISOString(),
    });
  }

  handleDisconnect(client: Socket) {
    this.connectedClients.delete(client.id);
    this.logger.log(`❌ Client disconnesso: ${client.id} (Remaining: ${this.connectedClients.size})`);
  }

  /**
   * 🔥 Notifica inizio enrichment
   */
  async notifyEnrichmentStarted(sessionId: string, totalMovies: number) {
    const payload = {
      sessionId,
      type: 'started',
      total: totalMovies,
      processed: 0,
      percentage: 0,
      message: `Enrichment avviato per ${totalMovies} film`,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:started', payload);
    this.logger.log(`📢 Enrichment started: ${totalMovies} film (session: ${sessionId})`);
  }

  /**
   * 🔥 Notifica progress enrichment (chiamato per ogni film)
   */
  async notifyEnrichmentProgress(
    sessionId: string,
    processed: number,
    total: number,
    currentMovie: string,
  ) {
    const percentage = Math.round((processed / total) * 100);

    const payload = {
      sessionId,
      type: 'progress',
      total,
      processed,
      percentage,
      currentMovie,
      message: `Processing: ${currentMovie}`,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:progress', payload);
    
    // Log ogni 10%
    if (percentage % 10 === 0 && processed > 0) {
      this.logger.log(`📊 Progress: ${processed}/${total} (${percentage}%)`);
    }
  }

  /**
   * 🔥 Notifica completamento enrichment
   */
  async notifyEnrichmentCompleted(
    sessionId: string,
    total: number,
    successful: number,
  ) {
    const payload = {
      sessionId,
      type: 'completed',
      total,
      processed: total,
      percentage: 100,
      successful,
      message: `Enrichment completato: ${successful}/${total} film`,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:completed', payload);
    this.logger.log(`✅ Enrichment completed: ${successful}/${total} film (session: ${sessionId})`);
  }

  /**
   * Notifica errore
   */
  async notifyEnrichmentError(sessionId: string, errorMessage: string) {
    const payload = {
      sessionId,
      type: 'error',
      message: errorMessage,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:error', payload);
    this.logger.error(`❌ Enrichment error (session: ${sessionId}): ${errorMessage}`);
  }

  /**
   * Broadcast generico a tutti i client
   */
  broadcastMessage(event: string, data: any) {
    this.server.emit(event, data);
  }

  /**
   * Info connessioni attive
   */
  getConnectionInfo() {
    return {
      totalConnections: this.connectedClients.size,
      clients: Array.from(this.connectedClients.keys()),
    };
  }
}