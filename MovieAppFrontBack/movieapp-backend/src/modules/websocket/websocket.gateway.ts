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

    //invia messaggio di benvenuto
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
   * notifica inizio enrichment
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
    this.logger.log(`🔢 Enrichment started: ${totalMovies} film (session: ${sessionId})`);
  }

  /**
   * notifica progress enrichment (chiamato per ogni film)
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
    
    //log ogni film (non solo ogni 10%)
    this.logger.log(`📊 Progress: ${processed}/${total} (${percentage}%) - ${currentMovie}`);
  }

  /**
   * notifica completamento enrichment
   */
  async notifyEnrichmentCompleted(sessionId: string, totalMovies: number) {
    const payload = {
      sessionId,
      type: 'completed',
      total: totalMovies,
      processed: totalMovies,
      percentage: 100,
      message: `Enrichment completato: ${totalMovies} film`,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:completed', payload);
    this.logger.log(`✅ Enrichment completed: ${totalMovies} film (session: ${sessionId})`);
  }

  /**
   * notifica errore enrichment
   */
  async notifyEnrichmentError(sessionId: string, error: string) {
    const payload = {
      sessionId,
      type: 'error',
      message: error,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:error', payload);
    this.logger.error(`❌ Enrichment error (session: ${sessionId}): ${error}`);
  }

  /**
   * invia messaggio custom a tutti i client
   */
  broadcast(event: string, data: any) {
    this.server.emit(event, data);
    this.logger.log(`📢 Broadcast: ${event}`);
  }

  /**
   * invia messaggio a client specifico
   */
  sendToClient(clientId: string, event: string, data: any) {
    const client = this.connectedClients.get(clientId);
    if (client) {
      client.emit(event, data);
      this.logger.log(`📤 Message to ${clientId}: ${event}`);
    }
  }

  /**
   * ottieni numero client connessi
   */
  getConnectedClientsCount(): number {
    return this.connectedClients.size;
  }
}