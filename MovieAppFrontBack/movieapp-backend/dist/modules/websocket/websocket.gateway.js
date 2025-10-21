"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var WebsocketGateway_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.WebsocketGateway = void 0;
const websockets_1 = require("@nestjs/websockets");
const common_1 = require("@nestjs/common");
const socket_io_1 = require("socket.io");
let WebsocketGateway = WebsocketGateway_1 = class WebsocketGateway {
    constructor() {
        this.logger = new common_1.Logger(WebsocketGateway_1.name);
        this.connectedClients = new Set();
        this.activeEnrichmentSessions = new Map();
    }
    afterInit(server) {
        this.logger.log('✅ websocket gateway inizializzato');
        this.logger.log(`namespace: /ws`);
    }
    handleConnection(client) {
        this.connectedClients.add(client.id);
        this.logger.log(`✅ client connesso: ${client.id} (totale: ${this.connectedClients.size})`);
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
    handleDisconnect(client) {
        this.connectedClients.delete(client.id);
        this.logger.log(`❌ client disconnesso: ${client.id} (totale: ${this.connectedClients.size})`);
    }
    async notifyEnrichmentProgress(sessionId, processed, total, currentMovie) {
        try {
            const percentage = total > 0 ? Math.round((processed / total) * 100) : 0;
            const message = {
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
            this.logger.debug(`📊 progress notificato: ${processed}/${total} (${percentage}%) - ${currentMovie || 'N/A'}`);
        }
        catch (error) {
            this.logger.error(`errore notifica progress: ${error.message}`);
        }
    }
    async notifyEnrichmentCompleted(sessionId, total, successful, cacheHits = 0) {
        try {
            const backendProcessed = successful - cacheHits;
            const successRate = total > 0 ? Math.round((successful / total) * 100) : 0;
            const message = {
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
            this.logger.log(`✅ enrichment completato: ${successful}/${total} (cache: ${cacheHits}, tmdb: ${backendProcessed})`);
            setTimeout(() => {
                this.notifyChartUpdate('all', successful);
            }, 1000);
        }
        catch (error) {
            this.logger.error(`errore notifica completed: ${error.message}`);
        }
    }
    async notifyEnrichmentError(sessionId, error) {
        try {
            const message = {
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
        }
        catch (err) {
            this.logger.error(`errore notifica error: ${err.message}`);
        }
    }
    async notifyBatchCompleted(sessionId, watchlistCount, watchedCount, total) {
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
        }
        catch (error) {
            this.logger.error(`errore notifica batch: ${error.message}`);
        }
    }
    async notifyChartUpdate(chartType, dataPoints) {
        try {
            const message = {
                type: chartType,
                status: 'generated',
                dataPoints,
                message: `grafico ${chartType} generato con ${dataPoints} elementi`,
            };
            this.server.emit('chart:update', message);
            this.logger.debug(`📈 grafico aggiornato: ${chartType} (${dataPoints} punti)`);
        }
        catch (error) {
            this.logger.error(`errore notifica chart: ${error.message}`);
        }
    }
    async notifyChartError(chartType, error) {
        try {
            const message = {
                type: chartType,
                status: 'error',
                message: `errore grafico ${chartType}: ${error}`,
                error,
            };
            this.server.emit('chart:error', message);
            this.logger.error(`❌ errore grafico ${chartType}: ${error}`);
        }
        catch (err) {
            this.logger.error(`errore notifica chart error: ${err.message}`);
        }
    }
    async notifySystem(type, message, data) {
        try {
            const notification = {
                type,
                message,
                data,
                timestamp: new Date().toISOString(),
            };
            this.server.emit('system:notification', notification);
            this.logger.debug(`🔔 notifica sistema: ${type} - ${message}`);
        }
        catch (error) {
            this.logger.error(`errore notifica sistema: ${error.message}`);
        }
    }
    getEnrichmentSession(sessionId) {
        return this.activeEnrichmentSessions.get(sessionId) || null;
    }
    cleanExpiredSessions() {
        try {
            const now = Date.now();
            const maxAge = 3600000;
            for (const [sessionId, session] of this.activeEnrichmentSessions) {
                const sessionTime = session.endTime || session.lastUpdate || session.startTime;
                const sessionAge = now - new Date(sessionTime).getTime();
                if (sessionAge > maxAge) {
                    this.activeEnrichmentSessions.delete(sessionId);
                    this.logger.debug(`🧹 sessione scaduta rimossa: ${sessionId}`);
                }
            }
        }
        catch (error) {
            this.logger.error(`errore pulizia sessioni: ${error.message}`);
        }
    }
    getConnectionInfo() {
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
    broadcastMessage(event, data) {
        try {
            this.server.emit(event, data);
            this.logger.debug(`📡 broadcast: ${event}`);
        }
        catch (error) {
            this.logger.error(`errore broadcast: ${error.message}`);
        }
    }
};
exports.WebsocketGateway = WebsocketGateway;
__decorate([
    (0, websockets_1.WebSocketServer)(),
    __metadata("design:type", socket_io_1.Server)
], WebsocketGateway.prototype, "server", void 0);
exports.WebsocketGateway = WebsocketGateway = WebsocketGateway_1 = __decorate([
    (0, websockets_1.WebSocketGateway)({
        cors: {
            origin: '*',
            credentials: true,
        },
        namespace: '/ws',
    })
], WebsocketGateway);
//# sourceMappingURL=websocket.gateway.js.map