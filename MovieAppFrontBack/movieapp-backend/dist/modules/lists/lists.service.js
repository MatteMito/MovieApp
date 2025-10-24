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
var ListsService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const list_entity_1 = require("../../database/entities/list.entity");
const movie_entity_1 = require("../../database/entities/movie.entity");
let ListsService = ListsService_1 = class ListsService {
    constructor(listRepository, movieRepository) {
        this.listRepository = listRepository;
        this.movieRepository = movieRepository;
        this.logger = new common_1.Logger(ListsService_1.name);
    }
    async createUserList(createDto) {
        try {
            const list = this.listRepository.create({
                user_id: createDto.user_id,
                name: createDto.name,
                description: createDto.description,
                is_public: createDto.is_public || false,
                movie_ids: createDto.movie_ids || [],
                target_date: createDto.target_date ? new Date(createDto.target_date) : null,
                frequency: createDto.frequency,
                followers_count: 0,
                follower_ids: [],
            });
            const saved = await this.listRepository.save(list);
            this.logger.log(`✅ Lista creata: ${saved.name} (${saved.id})`);
            return saved;
        }
        catch (error) {
            this.logger.error(`❌ Errore creazione lista: ${error.message}`);
            throw error;
        }
    }
    async getUserLists(userId) {
        try {
            const lists = await this.listRepository.find({
                where: { user_id: userId },
                order: { created_at: 'DESC' },
            });
            this.logger.log(`📋 ${lists.length} liste trovate per utente ${userId}`);
            return lists;
        }
        catch (error) {
            this.logger.error(`❌ Errore recupero liste utente: ${error.message}`);
            throw error;
        }
    }
    async getPublicLists(limit = 20) {
        try {
            const lists = await this.listRepository.find({
                where: { is_public: true },
                order: { followers_count: 'DESC', created_at: 'DESC' },
                take: limit,
            });
            this.logger.log(`🌍 ${lists.length} liste pubbliche recuperate`);
            return lists;
        }
        catch (error) {
            this.logger.error(`❌ Errore recupero liste pubbliche: ${error.message}`);
            throw error;
        }
    }
    async getListWithMovies(listId) {
        try {
            const list = await this.listRepository.findOne({
                where: { id: listId },
            });
            if (!list) {
                throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
            }
            let movies = [];
            if (list.movie_ids && list.movie_ids.length > 0) {
                movies = await this.movieRepository
                    .createQueryBuilder('movie')
                    .where('movie.id IN (:...ids)', { ids: list.movie_ids })
                    .getMany();
            }
            return {
                ...list,
                movies,
                movie_count: list.movie_ids.length,
            };
        }
        catch (error) {
            this.logger.error(`❌ Errore recupero lista: ${error.message}`);
            throw error;
        }
    }
    async updateList(listId, updateDto) {
        try {
            const list = await this.listRepository.findOne({
                where: { id: listId },
            });
            if (!list) {
                throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
            }
            if (updateDto.name !== undefined)
                list.name = updateDto.name;
            if (updateDto.description !== undefined)
                list.description = updateDto.description;
            if (updateDto.is_public !== undefined)
                list.is_public = updateDto.is_public;
            if (updateDto.movie_ids !== undefined)
                list.movie_ids = updateDto.movie_ids;
            if (updateDto.target_date !== undefined) {
                list.target_date = updateDto.target_date ? new Date(updateDto.target_date) : null;
            }
            if (updateDto.frequency !== undefined)
                list.frequency = updateDto.frequency;
            const updated = await this.listRepository.save(list);
            this.logger.log(`✅ Lista aggiornata: ${updated.id}`);
            return updated;
        }
        catch (error) {
            this.logger.error(`❌ Errore aggiornamento lista: ${error.message}`);
            throw error;
        }
    }
    async deleteList(listId) {
        try {
            const result = await this.listRepository.delete(listId);
            if (result.affected === 0) {
                throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
            }
            this.logger.log(`🗑️ Lista eliminata: ${listId}`);
        }
        catch (error) {
            this.logger.error(`❌ Errore eliminazione lista: ${error.message}`);
            throw error;
        }
    }
    async addMovieToList(listId, movieId) {
        try {
            const list = await this.listRepository.findOne({
                where: { id: listId },
            });
            if (!list) {
                throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
            }
            const movie = await this.movieRepository.findOne({
                where: { id: movieId },
            });
            if (!movie) {
                throw new common_1.NotFoundException(`Film ${movieId} non trovato`);
            }
            if (!list.movie_ids.includes(movieId)) {
                list.movie_ids.push(movieId);
                await this.listRepository.save(list);
                this.logger.log(`➕ Film ${movieId} aggiunto a lista ${listId}`);
            }
            else {
                this.logger.log(`⚠️ Film ${movieId} già presente in lista ${listId}`);
            }
            return list;
        }
        catch (error) {
            this.logger.error(`❌ Errore aggiunta film: ${error.message}`);
            throw error;
        }
    }
    async removeMovieFromList(listId, movieId) {
        try {
            const list = await this.listRepository.findOne({
                where: { id: listId },
            });
            if (!list) {
                throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
            }
            list.movie_ids = list.movie_ids.filter(id => id !== movieId);
            await this.listRepository.save(list);
            this.logger.log(`➖ Film ${movieId} rimosso da lista ${listId}`);
            return list;
        }
        catch (error) {
            this.logger.error(`❌ Errore rimozione film: ${error.message}`);
            throw error;
        }
    }
    async followList(listId, userId) {
        try {
            const list = await this.listRepository.findOne({
                where: { id: listId },
            });
            if (!list) {
                throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
            }
            if (!list.is_public) {
                throw new Error('Puoi seguire solo liste pubbliche');
            }
            if (!list.follower_ids.includes(userId)) {
                list.follower_ids.push(userId);
                list.followers_count = list.follower_ids.length;
                await this.listRepository.save(list);
                this.logger.log(`👥 Utente ${userId} segue lista ${listId}`);
            }
            else {
                this.logger.log(`⚠️ Utente ${userId} già segue lista ${listId}`);
            }
            return list;
        }
        catch (error) {
            this.logger.error(`❌ Errore follow lista: ${error.message}`);
            throw error;
        }
    }
};
exports.ListsService = ListsService;
exports.ListsService = ListsService = ListsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(list_entity_1.MovieListEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository])
], ListsService);
//# sourceMappingURL=lists.service.js.map