import { OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect } from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
export declare class WebsocketGateway implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect {
    server: Server;
    private readonly logger;
    private connectedClients;
    private activeEnrichmentSessions;
    afterInit(server: Server): void;
    handleConnection(client: Socket): void;
    handleDisconnect(client: Socket): void;
    notifyEnrichmentStarted(sessionId: string, total: number): Promise<void>;
    notifyEnrichmentProgress(sessionId: string, processed: number, total: number, currentMovie?: string): Promise<void>;
    notifyEnrichmentCompleted(sessionId: string, total: number, successful: number, cacheHits?: number): Promise<void>;
    notifyEnrichmentError(sessionId: string, error: string): Promise<void>;
    notifyBatchCompleted(sessionId: string, watchlistCount: number, watchedCount: number, total: number): Promise<void>;
    notifyChartUpdate(chartType: string, dataPoints: number): Promise<void>;
    notifyChartError(chartType: string, error: string): Promise<void>;
    notifySystem(type: string, message: string, data?: any): Promise<void>;
    getEnrichmentSession(sessionId: string): any | null;
    cleanExpiredSessions(): void;
    getConnectionInfo(): any;
    broadcastMessage(event: string, data: any): void;
}
