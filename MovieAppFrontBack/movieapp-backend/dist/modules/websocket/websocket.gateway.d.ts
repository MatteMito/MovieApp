import { OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect } from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
export declare class WebsocketGateway implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect {
    server: Server;
    private readonly logger;
    private connectedClients;
    afterInit(server: Server): void;
    handleConnection(client: Socket): void;
    handleDisconnect(client: Socket): void;
    notifyEnrichmentStarted(sessionId: string, totalMovies: number): Promise<void>;
    notifyEnrichmentProgress(sessionId: string, processed: number, total: number, currentMovie: string): Promise<void>;
    notifyEnrichmentCompleted(sessionId: string, totalMovies: number): Promise<void>;
    notifyEnrichmentError(sessionId: string, error: string): Promise<void>;
    broadcast(event: string, data: any): void;
    sendToClient(clientId: string, event: string, data: any): void;
    getConnectedClientsCount(): number;
}
