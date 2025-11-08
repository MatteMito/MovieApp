// gateway websocket per notifiche real-time con socket.io
// gestisce connessioni client, notifiche enrichment progress, broadcast eventi

import {
  WebSocketGateway,
  WebSocketServer,
  OnGatewayInit,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Logger } from '@nestjs/common';
import { Server, Socket } from 'socket.io';

// decoratore websocket gateway
// namespace: /ws per separare da altri websocket
// cors: permette connessioni da qualsiasi origine (android app)
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
  // server socket.io iniettato automaticamente
  @WebSocketServer()
  server: Server;

  private readonly logger = new Logger(WebsocketGateway.name);
  
  // mappa client connessi per invio messaggi mirati
  private connectedClients = new Map<string, Socket>();

  /**
   * chiamato dopo inizializzazione gateway
   * setup completato, pronto per connessioni
   */
  afterInit(server: Server) {
    this.logger.log('websocket gateway inizializzato');
    this.logger.log('namespace: /ws');
  }

  /**
   * chiamato quando nuovo client si connette
   * invia messaggio benvenuto e registra client
   */
  handleConnection(client: Socket) {
    // registra client nella mappa
    this.connectedClients.set(client.id, client);
    
    this.logger.log(`client connesso: ${client.id} (totale: ${this.connectedClients.size})`);

    // invia messaggio di benvenuto al client
    client.emit('connection', {
      message: 'connesso al server websocket',
      clientId: client.id,
      timestamp: new Date().toISOString(),
    });
  }

  /**
   * chiamato quando client si disconnette
   * rimuove client dalla mappa
   */
  handleDisconnect(client: Socket) {
    this.connectedClients.delete(client.id);
    this.logger.log(`client disconnesso: ${client.id} (rimanenti: ${this.connectedClients.size})`);
  }

  // ===== metodi pubblici per notifiche enrichment =====

  /**
   * notifica inizio enrichment batch
   * usato da movies service quando inizia enrichment
   */
  async notifyEnrichmentStarted(sessionId: string, totalMovies: number) {
    const payload = {
      sessionId,
      type: 'started',
      total: totalMovies,
      processed: 0,
      percentage: 0,
      message: `enrichment avviato per ${totalMovies} film`,
      timestamp: new Date().toISOString(),
    };

    // broadcast a tutti i client connessi
    this.server.emit('enrichment:started', payload);
    
    this.logger.log(`enrichment started: ${totalMovies} film (session: ${sessionId})`);
  }

  /**
   * notifica progresso enrichment
   * chiamato per ogni film processato per aggiornare progress bar android
   */
  async notifyEnrichmentProgress(
    sessionId: string,
    processed: number,
    total: number,
    currentMovie: string,
  ) {
    // calcola percentuale completamento
    const percentage = Math.round((processed / total) * 100);

    const payload = {
      sessionId,
      type: 'progress',
      total,
      processed,
      percentage,
      currentMovie,
      message: `processing: ${currentMovie}`,
      timestamp: new Date().toISOString(),
    };

    // broadcast progresso a tutti i client
    this.server.emit('enrichment:progress', payload);
    
    // log ogni film per debug
    this.logger.log(`progress: ${processed}/${total} (${percentage}%) - ${currentMovie}`);
  }

  /**
   * notifica completamento enrichment
   * chiamato quando tutti i film sono stati processati
   */
  async notifyEnrichmentCompleted(sessionId: string, totalMovies: number) {
    const payload = {
      sessionId,
      type: 'completed',
      total: totalMovies,
      processed: totalMovies,
      percentage: 100,
      message: `enrichment completato: ${totalMovies} film`,
      timestamp: new Date().toISOString(),
    };

    // broadcast completamento a tutti i client
    this.server.emit('enrichment:completed', payload);
    
    this.logger.log(`enrichment completed: ${totalMovies} film (session: ${sessionId})`);
  }

  /**
   * notifica errore durante enrichment
   */
  async notifyEnrichmentError(sessionId: string, error: string) {
    const payload = {
      sessionId,
      type: 'error',
      message: error,
      timestamp: new Date().toISOString(),
    };

    this.server.emit('enrichment:error', payload);
    this.logger.error(`enrichment error (session: ${sessionId}): ${error}`);
  }

  // ===== metodi utility =====

  /**
   * invia messaggio custom a tutti i client (broadcast)
   */
  broadcast(event: string, data: any) {
    this.server.emit(event, data);
    this.logger.log(`broadcast: ${event}`);
  }

  /**
   * invia messaggio a client specifico
   */
  sendToClient(clientId: string, event: string, data: any) {
    const client = this.connectedClients.get(clientId);
    if (client) {
      client.emit(event, data);
      this.logger.log(`message to ${clientId}: ${event}`);
    }
  }

  /**
   * ottieni numero client connessi
   */
  getConnectedClientsCount(): number {
    return this.connectedClients.size;
  }
}