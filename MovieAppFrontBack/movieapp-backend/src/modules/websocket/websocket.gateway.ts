// websocket gateway per notifiche real-time (progress enrichment)

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
  namespace: '/ws', // endpoint websocket
  cors: {
    origin: '*', // permetti tutti i domini (dev)
    credentials: true,
  },
})
export class WebsocketGateway
  implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server: Server; // server socket.io

  private readonly logger = new Logger(WebsocketGateway.name);
  private connectedClients = new Map<string, Socket>(); // traccia client connessi

  // chiamato dopo inizializzazione gateway
  afterInit(server: Server) {
    this.logger.log('🔌 WebSocket Gateway inizializzato');
    this.logger.log('   Namespace: /ws');
  }

  // chiamato quando client si connette
  handleConnection(client: Socket) {
    this.connectedClients.set(client.id, client);
    this.logger.log(`✅ Client connesso: ${client.id} (Total: ${this.connectedClients.size})`);

    // invia messaggio di benvenuto al client
    client.emit('connection', {
      message: 'Connesso al server WebSocket',
      clientId: client.id,
      timestamp: new Date().toISOString(),
    });
  }

  // chiamato quando client si disconnette
  handleDisconnect(client: Socket) {
    this.connectedClients.delete(client.id);
    this.logger.log(`❌ Client disconnesso: ${client.id} (Remaining: ${this.connectedClients.size})`);
  }

  // notifica inizio enrichment batch
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

    // broadcast a tutti i client connessi
    this.server.emit('enrichment:started', payload);
    this.logger.log(`🔢 Enrichment started: ${totalMovies} film (session: ${sessionId})`);
  }

  // notifica progress enrichment (chiamato per ogni film processato)
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

    // broadcast progress a tutti i client
    this.server.emit('enrichment:progress', payload);
    
    // log ogni film processato
    this.logger.log(`📊 Progress: ${processed}/${total} (${percentage}%) - ${currentMovie}`);
  }

  // notifica completamento enrichment
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

    // broadcast completamento a tutti i client
    this.server.emit('enrichment:completed', payload);
    this.logger.log(`✅ Enrichment completed: ${totalMovies} film (session: ${sessionId})`);
  }

  // notifica errore durante enrichment
  async notifyEnrichmentError(sessionId: string, error: string) {
    const payload = {
      sessionId,
      type: 'error',
      message: error,
      timestamp: new Date().toISOString(),
    };

    // broadcast errore a tutti i client
    this.server.emit('enrichment:error', payload);
    this.logger.error(`❌ Enrichment error (session: ${sessionId}): ${error}`);
  }

  // invia messaggio custom a tutti i client connessi
  broadcast(event: string, data: any) {
    this.server.emit(event, data);
    this.logger.log(`📢 Broadcast: ${event}`);
  }

  // invia messaggio a client specifico per id
  sendToClient(clientId: string, event: string, data: any) {
    const client = this.connectedClients.get(clientId);
    if (client) {
      client.emit(event, data);
      this.logger.log(`📤 Message to ${clientId}: ${event}`);
    }
  }

  // ottieni numero di client attualmente connessi
  getConnectedClientsCount(): number {
    return this.connectedClients.size;
  }
}