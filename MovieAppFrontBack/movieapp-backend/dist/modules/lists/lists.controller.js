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
const list_dto_1 = require("../../common/dto/list.dto");
let ListsController = ListsController_1 = class ListsController {
    constructor(listsService) {
        this.listsService = listsService;
        this.logger = new common_1.Logger(ListsController_1.name);
    }
    async createList(createDto) {
        try {
            this.logger.log(`📝 Creazione lista: ${createDto.name}`);
            const list = await this.listsService.createUserList(createDto);
            return {
                success: true,
                data: list,
                message: 'Lista creata con successo',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore creazione lista: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getUserLists(userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`📋 Recupero liste per utente ${userId}`);
            const lists = await this.listsService.getUserLists(userId);
            return {
                success: true,
                data: lists,
                message: `${lists.length} liste trovate`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore recupero liste: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getPublicLists(limit) {
        try {
            const limitNum = limit ? parseInt(limit, 10) : 20;
            this.logger.log(`🌍 Recupero liste pubbliche (limit: ${limitNum})`);
            const lists = await this.listsService.getPublicLists(limitNum);
            return {
                success: true,
                data: lists,
                message: `${lists.length} liste pubbliche`,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore liste pubbliche: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async getListById(listId) {
        try {
            this.logger.log(`📄 Recupero dettaglio lista ${listId}`);
            const list = await this.listsService.getListWithMovies(listId);
            if (!list) {
                throw new common_1.HttpException({ success: false, message: 'Lista non trovata', timestamp: new Date().toISOString() }, common_1.HttpStatus.NOT_FOUND);
            }
            return {
                success: true,
                data: list,
                message: 'Lista trovata',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore recupero lista: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, error.status || common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async updateList(listId, updateDto) {
        try {
            this.logger.log(`✏️ Aggiornamento lista ${listId}`);
            const list = await this.listsService.updateList(listId, updateDto);
            return {
                success: true,
                data: list,
                message: 'Lista aggiornata',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore aggiornamento lista: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async deleteList(listId) {
        try {
            this.logger.log(`🗑️ Eliminazione lista ${listId}`);
            await this.listsService.deleteList(listId);
            return {
                success: true,
                message: 'Lista eliminata',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore eliminazione lista: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async addMovieToList(listId, addMovieDto) {
        try {
            this.logger.log(`➕ Aggiunta film ${addMovieDto.movie_id} a lista ${listId}`);
            const list = await this.listsService.addMovieToList(listId, addMovieDto.movie_id);
            return {
                success: true,
                data: list,
                message: 'Film aggiunto alla lista',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore aggiunta film: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async removeMovieFromList(listId, movieId) {
        try {
            this.logger.log(`➖ Rimozione film ${movieId} da lista ${listId}`);
            const list = await this.listsService.removeMovieFromList(listId, movieId);
            return {
                success: true,
                data: list,
                message: 'Film rimosso dalla lista',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore rimozione film: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    async followList(listId, userId) {
        try {
            if (!userId) {
                throw new common_1.HttpException({ success: false, message: 'userId mancante', timestamp: new Date().toISOString() }, common_1.HttpStatus.BAD_REQUEST);
            }
            this.logger.log(`👥 Utente ${userId} segue lista ${listId}`);
            const list = await this.listsService.followList(listId, userId);
            return {
                success: true,
                data: list,
                message: 'Lista seguita con successo',
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore follow lista: ${error.message}`);
            throw new common_1.HttpException({ success: false, message: error.message, timestamp: new Date().toISOString() }, common_1.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
};
exports.ListsController = ListsController;
__decorate([
    (0, common_1.Post)(),
    __param(0, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [list_dto_1.CreateListDto]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "createList", null);
__decorate([
    (0, common_1.Get)(),
    __param(0, (0, common_1.Query)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getUserLists", null);
__decorate([
    (0, common_1.Get)('public'),
    __param(0, (0, common_1.Query)('limit')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getPublicLists", null);
__decorate([
    (0, common_1.Get)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "getListById", null);
__decorate([
    (0, common_1.Put)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, list_dto_1.UpdateListDto]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "updateList", null);
__decorate([
    (0, common_1.Delete)(':id'),
    __param(0, (0, common_1.Param)('id')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "deleteList", null);
__decorate([
    (0, common_1.Post)(':id/movies'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, list_dto_1.AddMovieToListDto]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "addMovieToList", null);
__decorate([
    (0, common_1.Delete)(':id/movies/:movieId'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Param)('movieId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "removeMovieFromList", null);
__decorate([
    (0, common_1.Post)(':id/follow'),
    __param(0, (0, common_1.Param)('id')),
    __param(1, (0, common_1.Body)('userId')),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [String, String]),
    __metadata("design:returntype", Promise)
], ListsController.prototype, "followList", null);
exports.ListsController = ListsController = ListsController_1 = __decorate([
    (0, common_1.Controller)('api/v1/lists'),
    __metadata("design:paramtypes", [lists_service_1.ListsService])
], ListsController);
//# sourceMappingURL=lists.controller.js.map