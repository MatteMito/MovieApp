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
    notifyEnrichmentCompleted(sessionId: string, total: number, successful: number): Promise<void>;
    notifyEnrichmentError(sessionId: string, errorMessage: string): Promise<void>;
    broadcastMessage(event: string, data: any): void;
    getConnectionInfo(): {
        totalConnections: number;
        clients: string[];
    };
}
