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
var TmdbScheduler_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.TmdbScheduler = void 0;
const common_1 = require("@nestjs/common");
const schedule_1 = require("@nestjs/schedule");
const tmdb_service_1 = require("./tmdb.service");
const config_1 = require("@nestjs/config");
let TmdbScheduler = TmdbScheduler_1 = class TmdbScheduler {
    constructor(tmdbService, configService) {
        this.tmdbService = tmdbService;
        this.configService = configService;
        this.logger = new common_1.Logger(TmdbScheduler_1.name);
        this.enableAutoSync =
            this.configService.get('ENABLE_AUTO_SYNC') === 'true';
        if (this.enableAutoSync) {
            this.logger.log('✅ auto sync tmdb abilitato');
        }
        else {
            this.logger.log('⚠️ auto sync tmdb disabilitato (abilita con ENABLE_AUTO_SYNC=true)');
        }
    }
    async syncPopularMoviesFull() {
        if (!this.enableAutoSync) {
            return;
        }
        this.logger.log('🔄 avvio sync completo mensile (10.000 film)');
        try {
            const result = await this.tmdbService.syncPopularMovies(10000);
            this.logger.log('✅ sync completo completato:');
            this.logger.log(`   sincronizzati: ${result.synced}`);
            this.logger.log(`   errori: ${result.errors}`);
        }
        catch (error) {
            this.logger.error(`❌ errore sync completo: ${error.message}`);
        }
    }
    async syncPopularMoviesIncremental() {
        if (!this.enableAutoSync) {
            return;
        }
        this.logger.log('🔄 avvio sync incrementale settimanale (500 film)');
        try {
            const result = await this.tmdbService.syncPopularMovies(500);
            this.logger.log('✅ sync incrementale completato:');
            this.logger.log(`   sincronizzati: ${result.synced}`);
            this.logger.log(`   errori: ${result.errors}`);
        }
        catch (error) {
            this.logger.error(`❌ errore sync incrementale: ${error.message}`);
        }
    }
    async cleanupUnusedMovies() {
        if (!this.enableAutoSync) {
            return;
        }
        this.logger.log('🧹 avvio pulizia film non usati');
        this.logger.log('⚠️ pulizia non ancora implementata');
    }
    async testSync() {
        this.logger.log('🧪 test sync manuale (10 film)');
        try {
            const result = await this.tmdbService.syncPopularMovies(10);
            this.logger.log('✅ test sync completato:');
            this.logger.log(`   sincronizzati: ${result.synced}`);
            this.logger.log(`   errori: ${result.errors}`);
            return result;
        }
        catch (error) {
            this.logger.error(`❌ errore test sync: ${error.message}`);
            throw error;
        }
    }
};
exports.TmdbScheduler = TmdbScheduler;
__decorate([
    (0, schedule_1.Cron)('0 3 1 * *', {
        name: 'sync-popular-full',
        timeZone: 'Europe/Rome',
    }),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], TmdbScheduler.prototype, "syncPopularMoviesFull", null);
__decorate([
    (0, schedule_1.Cron)('0 2 * * 1', {
        name: 'sync-popular-incremental',
        timeZone: 'Europe/Rome',
    }),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], TmdbScheduler.prototype, "syncPopularMoviesIncremental", null);
__decorate([
    (0, schedule_1.Cron)('0 4 * * 0', {
        name: 'cleanup-unused-movies',
        timeZone: 'Europe/Rome',
    }),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", []),
    __metadata("design:returntype", Promise)
], TmdbScheduler.prototype, "cleanupUnusedMovies", null);
exports.TmdbScheduler = TmdbScheduler = TmdbScheduler_1 = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [tmdb_service_1.TmdbService,
        config_1.ConfigService])
], TmdbScheduler);
//# sourceMappingURL=tmdb.scheduler.js.map