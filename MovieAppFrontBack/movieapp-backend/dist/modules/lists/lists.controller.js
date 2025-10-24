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
var ListsController_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsController = void 0;
const common_1 = require("@nestjs/common");
const lists_service_1 = require("./lists.service");
let ListsController = ListsController_1 = class ListsController {
    constructor(listsService) {
        this.listsService = listsService;
        this.logger = new common_1.Logger(ListsController_1.name);
    }
    async getTopRatedMovies(userId, limit) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            const limitNum = limit ? parseInt(limit, 10) : 20;
            this.logger.log(`🎬 top rated per utente ${userId}`);
            const list = await this.listsService.createCustomList('Top Rated', {
                userId,
                minRating: 7,
                sortBy: 'rating',
                sortOrder: 'DESC',
            }, 'Film con i voti più alti');
            list.movies = list.movies.slice(0, limitNum);
            list.totalMovies = list.movies.length;
            return {
                success: true,
                data: list,
                message: `top ${limitNum} film`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore top rated: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getRecentMovies(userId, limit) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            const limitNum = limit ? parseInt(limit, 10) : 20;
            this.logger.log(`🎬 film recenti per utente ${userId}`);
            const list = await this.listsService.createCustomList('Recent Movies', {
                userId,
                sortBy: 'year',
                sortOrder: 'DESC',
            }, 'Film più recenti');
            list.movies = list.movies.slice(0, limitNum);
            list.totalMovies = list.movies.length;
            return {
                success: true,
                data: list,
                message: `${limitNum} film recenti`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore recent movies: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getClassicMovies(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`🎬 classici per utente ${userId}`);
            const list = await this.listsService.createCustomList('Classics', {
                userId,
                maxYear: 1980,
                minRating: 7,
                sortBy: 'year',
                sortOrder: 'ASC',
            }, 'Film classici (pre-1980)');
            return {
                success: true,
                data: list,
                message: 'film classici',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore classics: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getLongMovies(userId, minRuntime) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            const minRuntimeNum = minRuntime ? parseInt(minRuntime, 10) : 150;
            this.logger.log(`🎬 film lunghi per utente ${userId}`);
            const list = await this.listsService.createCustomList('Long Movies', {
                userId,
                sortBy: 'runtime',
                sortOrder: 'DESC',
            }, `Film con durata >= ${minRuntimeNum} minuti`);
            list.movies = list.movies.filter(m => (m.runtime || 0) >= minRuntimeNum);
            list.totalMovies = list.movies.length;
            return {
                success: true,
                data: list,
                message: 'film lunghi',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore long movies: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getMoviesByGenre(userId, genre) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            if (!genre) {
                throw new common_1.HttpException({ success: false, message: 'genre mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`🎬 film per genere ${genre} per utente ${userId}`);
            const list = await this.listsService.createCustomList(`${genre} Movies`, {
                userId,
                genre,
                sortBy: 'rating',
                sortOrder: 'DESC',
            }, `Film del genere ${genre}`);
            return {
                success: true,
                data: list,
                message: `film genere ${genre}`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore by genre: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getMoviesByDirector(userId, director) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            if (!director) {
                throw new common_1.HttpException({ success: false, message: 'director mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`🎬 film per regista ${director} per utente ${userId}`);
            const list = await this.listsService.createCustomList(`${director} Films`, {
                userId,
                director,
                sortBy: 'year',
                sortOrder: 'DESC',
            }, `Film diretti da ${director}`);
            return {
                success: true,
                data: list,
                message: `film di ${director}`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore by director: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getPresetLists(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`🎬 preset lists per utente ${userId}`);
            const presetLists = await this.listsService.getPresetLists(userId);
            return {
                success: true,
                data: presetLists,
                message: 'preset lists',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore preset lists: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.ListsController = ListsController;
__decorate([
    (0, common_1.Get)('top-rated'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getTopRatedMovies", null);
__decorate([
    (0, common_1.Get)('recent'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getRecentMovies", null);
__decorate([
    (0, common_1.Get)('classics'),
    __param(0, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getClassicMovies", null);
__decorate([
    (0, common_1.Get)('long'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('minRuntime')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getLongMovies", null);
__decorate([
    (0, common_1.Get)('by-genre'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('genre')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getMoviesByGenre", null);
__decorate([
    (0, common_1.Get)('by-director'),
    __param(0, (0, common_1.Query)('userId')),
    __param(1, (0, common_1.Query)('director')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getMoviesByDirector", null);
__decorate([
    (0, common_1.Get)('preset'),
    __param(0, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getPresetLists", null);
exports.ListsController = ListsController = ListsController_1 = __decorate([
    (0, common_1.Controller)('api/v1/lists'),
    __metadata("design:paramtypes", [lists_service_1.ListsService])
], ListsController);
//# sourceMappingURL=lists.controller.js.map