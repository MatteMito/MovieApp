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
var TmdbController_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbController = void 0;
const common_1 = require("@nestjs/common");
const tmdb_service_1 = require("./tmdb.service");
const database_service_1 = require("../../database/database.service");
let TmdbController = TmdbController_1 = class TmdbController {
    constructor(tmdbService, databaseService) {
        this.tmdbService = tmdbService;
        this.databaseService = databaseService;
        this.logger = new common_1.Logger(TmdbController_1.name);
    }
    async autocompleteMovies(query, limit) {
        try {
            if (!query || query.trim().length === 0) {
                return {
                    success: true,
                    data: [],
                    message: 'query vuota',
                    timestamp: new Date().toISOString(),
                };
            }
            const parsedLimit = limit ? parseInt(limit, 10) : 10;
            this.logger.log(`autocomplete: "${query}" (limit: ${parsedLimit})`);
            const movies = await this.tmdbService.searchForAutocomplete(query, parsedLimit);
            this.logger.log(`trovati ${movies.length} film`);
            return {
                success: true,
                data: movies,
                message: `trovati ${movies.length} film`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore autocomplete: ${error.message}`);
            throw new common_1.HttpException({
                success: false,
                message: error.message,
                timestamp: new Date().toISOString()
            }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async syncPopularMovies(body) {
        try {
            const limit = body.limit || 10000;
            this.logger.log(`avvio sync ${limit} film popolari...`);
            const result = await this.tmdbService.syncPopularMovies(limit);
            return {
                success: true,
                data: result,
                message: `sincronizzati ${result.synced} film (${result.errors} errori)`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore sync: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getStats() {
        try {
            const stats = await this.databaseService.getSyncStats();
            return {
                success: true,
                data: stats,
                message: 'statistiche database',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore stats: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.TmdbController = TmdbController;
__decorate([
    (0, common_1.Get)('autocomplete'),
    __param(0, (0, common_1.Query)('query')),
    __param(1, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], TmdbController.prototype, "autocompleteMovies", null);
__decorate([
    (0, common_1.Post)('sync-popular'),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], TmdbController.prototype, "syncPopularMovies", null);
__decorate([
    (0, common_1.Get)('stats'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], TmdbController.prototype, "getStats", null);
exports.TmdbController = TmdbController = TmdbController_1 = __decorate([
    (0, common_1.Controller)('api/v1/tmdb'),
    __metadata("design:paramtypes", [tmdb_service_1.TmdbService,
        database_service_1.DatabaseService])
], TmdbController);
//# sourceMappingURL=tmdb.controller.js.map