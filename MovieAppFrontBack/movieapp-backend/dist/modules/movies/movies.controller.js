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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var MoviesController_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.MoviesController = void 0;
const common_1 = require("@nestjs/common");
const movies_service_1 = require("./movies.service");
let MoviesController = MoviesController_1 = class MoviesController {
    constructor(moviesService) {
        this.moviesService = moviesService;
        this.logger = new common_1.Logger(MoviesController_1.name);
    }
    async healthCheck() {
        try {
            this.logger.log('health check richiesto');
            const health = await this.moviesService.healthCheck();
            return {
                success: true,
                data: health,
                message: 'sistema operativo',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore health check: ${error.message}`);
            return {
                success: false,
                message: 'sistema degradato',
                timestamp: new Date().toISOString(),
                debug: { error: error.message },
            };
        }
    }
    async getCacheStats() {
        try {
            this.logger.log('statistiche cache richieste');
            const stats = await this.moviesService.getStats();
            return {
                success: true,
                data: stats,
                message: 'statistiche recuperate',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero statistiche',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async initializeApp(userId) {
        try {
            this.logger.log('richiesta inizializzazione app');
            const result = await this.moviesService.initializeApp();
            return {
                success: true,
                data: result,
                message: result.needsSync
                    ? 'inizializzazione avviata in background'
                    : 'database gia inizializzato',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore inizializzazione: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: `errore inizializzazione: ${error.message}`,
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async enrichMovies(body) {
        try {
            this.logger.log(`richiesta enrichment per ${body.movies.length} film`);
            if (!body.movies || body.movies.length === 0) {
                throw new common_1.HttpException({
                    success: false,
                    message: 'nessun film fornito per enrichment',
                    timestamp: new Date().toISOString(),
                }, common_1.HttpStatus.BAD_REQUEST);
            }
            const result = await this.moviesService.enrichMovies(body.movies);
            const enrichedCount = result.successfulMovies.filter((m) => m.tmdb_id).length;
            this.logger.log(`enrichment completato: ${enrichedCount}/${body.movies.length} film`);
            return {
                success: true,
                data: result,
                message: `enrichment completato: ${enrichedCount}/${body.movies.length} film arricchiti`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore enrichment: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: `errore enrichment: ${error.message}`,
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async batchUpload(body, headerUserId) {
        try {
            const userId = body.userId || headerUserId;
            if (!userId) {
                throw new common_1.HttpException({
                    success: false,
                    message: 'userId mancante. fornire userId nel body o nell\'header user-id',
                    timestamp: new Date().toISOString(),
                }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`batch upload per utente ${userId}: ${body.watchlist.length} watchlist + ${body.watched.length} watched`);
            const result = await this.moviesService.batchUploadWithUserAssociation(userId, body.watchlist, body.watched);
            this.logger.log(`batch upload completato: ${result.summary.totalEnriched} film arricchiti`);
            this.logger.log(`contatori: ${result.importCounters.watchedFromFile} watched e ${result.importCounters.watchlistFromFile} watchlist nel file`);
            return {
                success: true,
                data: {
                    ...result,
                    counters: {
                        fromFile: {
                            watched: result.importCounters.watchedFromFile,
                            watchlist: result.importCounters.watchlistFromFile,
                            total: result.importCounters.watchedFromFile + result.importCounters.watchlistFromFile,
                        },
                        afterRefresh: {
                            watched: result.importCounters.totalWatched,
                            watchlist: result.importCounters.totalWatchlist,
                            total: result.importCounters.totalWatched + result.importCounters.totalWatchlist,
                        },
                    },
                },
                message: `batch completato: ${result.summary.totalMovies} film processati per utente ${userId}`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore batch upload: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: `errore batch upload: ${error.message}`,
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getUserMovies(userId, status) {
        try {
            this.logger.log(`richiesta film per user ${userId} (status: ${status || 'all'})`);
            const movies = await this.moviesService.getUserMovies(userId, status);
            return {
                success: true,
                data: { movies },
                message: `recuperati ${movies.length} film`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore recupero film: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getUserStats(userId) {
        try {
            this.logger.log(`richiesta statistiche per user ${userId}`);
            const stats = await this.moviesService.getUserStats(userId);
            return {
                success: true,
                data: stats,
                message: 'statistiche recuperate',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche utente: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero statistiche',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getAllMovies(userId) {
        this.logger.warn('endpoint /all deprecato. usare /user/:userId');
        try {
            if (!userId) {
                throw new common_1.HttpException({
                    success: false,
                    message: 'userId richiesto negli headers (x-user-id)',
                    timestamp: new Date().toISOString(),
                }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log('richiesta tutti i film (deprecato)');
            const movies = await this.moviesService.getUserMovies(userId);
            return {
                success: true,
                data: { movies },
                message: `recuperati ${movies.length} film - attenzione: endpoint deprecato, usare /user/:userId`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore recupero film: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: 'errore recupero film',
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async deleteAllUserMovies(userId) {
        try {
            this.logger.log(`richiesta eliminazione associazioni film per user ${userId}`);
            const result = await this.moviesService.deleteAllMovies(userId);
            this.logger.log(`eliminate ${result.deleted} associazioni film`);
            return {
                success: true,
                data: result,
                message: `eliminate ${result.deleted} associazioni film`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore eliminazione associazioni: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: `errore eliminazione associazioni: ${error.message}`,
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async deleteAllMovies(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({
                    success: false,
                    message: 'userid richiesto per operazioni di eliminazione',
                    timestamp: new Date().toISOString(),
                }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`richiesta eliminazione completa film (user: ${userId})`);
            const result = await this.moviesService.deleteAllMovies(userId);
            return {
                success: true,
                data: result,
                message: `eliminati ${result.deleted} film`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore eliminazione film: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: `errore eliminazione: ${error.message}`,
                timestamp: new Date().toISOString(),
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.MoviesController = MoviesController;
__decorate([
    (0, common_1.Get)('health'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "healthCheck", null);
__decorate([
    (0, common_1.Get)('cache/stats'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "getCacheStats", null);
__decorate([
    (0, common_1.Get)('initialize'),
    __param(0, (0, common_1.Headers)('x-user-id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "initializeApp", null);
__decorate([
    (0, common_1.Post)('enrich'),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "enrichMovies", null);
__decorate([
    (0, common_1.Post)('batch'),
    __param(0, (0, common_1.Body)()),
    __param(1, (0, common_1.Headers)('user-id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object, String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "batchUpload", null);
__decorate([
    (0, common_1.Get)('user/:userId'),
    __param(0, (0, common_1.Param)('userId')),
    __param(1, (0, common_1.Query)('status')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "getUserMovies", null);
__decorate([
    (0, common_1.Get)('user/:userId/stats'),
    __param(0, (0, common_1.Param)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "getUserStats", null);
__decorate([
    (0, common_1.Get)('all'),
    __param(0, (0, common_1.Headers)('x-user-id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "getAllMovies", null);
__decorate([
    (0, common_1.Delete)('user/:userId/all'),
    __param(0, (0, common_1.Param)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "deleteAllUserMovies", null);
__decorate([
    (0, common_1.Delete)('all'),
    __param(0, (0, common_1.Headers)('x-user-id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], MoviesController.prototype, "deleteAllMovies", null);
exports.MoviesController = MoviesController = MoviesController_1 = __decorate([
    (0, common_1.Controller)('api/v1/movies'),
    __metadata("design:paramtypes", [movies_service_1.MoviesService])
], MoviesController);
//# sourceMappingURL=movies.controller.js.map