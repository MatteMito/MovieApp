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
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const list_entity_1 = require("../../database/entities/list.entity");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_entity_1 = require("../../database/entities/user.entity");
let ListsService = class ListsService {
    constructor(listRepository, movieRepository, userRepository) {
        this.listRepository = listRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
    }
    async getUserLists(userId) {
        const lists = await this.listRepository.find({
            where: { user_id: userId },
            order: { created_at: 'DESC' },
        });
        const populatedLists = await Promise.all(lists.map(async (list) => {
            const movies = await this.getMoviesForList(list.movie_ids);
            return {
                ...list,
                movies,
            };
        }));
        return populatedLists;
    }
    async getPublicLists(filters) {
        let query = this.listRepository.createQueryBuilder('list')
            .where('list.is_public = :isPublic', { isPublic: true });
        if (filters?.search) {
            query = query.andWhere('(list.name ILIKE :search OR list.description ILIKE :search)', { search: `%${filters.search}%` });
        }
        switch (filters?.sortBy) {
            case 'name':
                query = query.orderBy('list.name', 'ASC');
                break;
            case 'created_at':
            default:
                query = query.orderBy('list.created_at', 'DESC');
                break;
        }
        const lists = await query.getMany();
        const populatedLists = await Promise.all(lists.map(async (list) => {
            const movies = await this.getMoviesForList(list.movie_ids);
            return {
                ...list,
                movies,
            };
        }));
        return populatedLists;
    }
    async getListById(listId, userId) {
        const list = await this.listRepository.findOne({
            where: { id: listId },
        });
        if (!list) {
            throw new common_1.NotFoundException(`Lista ${listId} non trovata`);
        }
        if (!list.is_public && list.user_id !== userId) {
            throw new common_1.ForbiddenException('Non hai accesso a questa lista');
        }
        const movies = await this.getMoviesForList(list.movie_ids);
        return {
            ...list,
            movies,
        };
    }
    async createList(userId, createListDto) {
        const user = await this.userRepository.findOne({ where: { id: userId } });
        if (!user) {
            throw new common_1.NotFoundException('Utente non trovato');
        }
        const newList = this.listRepository.create({
            user_id: userId,
            name: createListDto.name,
            description: createListDto.description || null,
            is_public: createListDto.is_public || false,
            movie_ids: [],
            follower_ids: [],
            followers_count: 0,
        });
        return this.listRepository.save(newList);
    }
    async updateList(listId, userId, updateListDto) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('Non puoi modificare questa lista');
        }
        if (updateListDto.name !== undefined) {
            list.name = updateListDto.name;
        }
        if (updateListDto.description !== undefined) {
            list.description = updateListDto.description;
        }
        if (updateListDto.is_public !== undefined) {
            list.is_public = updateListDto.is_public;
        }
        return this.listRepository.save(list);
    }
    async deleteList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('Non puoi eliminare questa lista');
        }
        await this.listRepository.remove(list);
        return { message: 'Lista eliminata con successo' };
    }
    async addMovieToList(listId, userId, movieId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('Non puoi modificare questa lista');
        }
        const movie = await this.movieRepository.findOne({ where: { id: movieId } });
        if (!movie) {
            throw new common_1.NotFoundException(`Film ${movieId} non trovato`);
        }
        if (list.movie_ids.includes(movieId)) {
            throw new common_1.BadRequestException('Film già presente nella lista');
        }
        list.movie_ids = [...list.movie_ids, movieId];
        const updatedList = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(updatedList.movie_ids);
        return {
            ...updatedList,
            movies,
        };
    }
    async removeMovieFromList(listId, userId, movieId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('Non puoi modificare questa lista');
        }
        list.movie_ids = list.movie_ids.filter((id) => id !== movieId);
        const updatedList = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(updatedList.movie_ids);
        return {
            ...updatedList,
            movies,
        };
    }
    async followList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        if (!list.is_public) {
            throw new common_1.ForbiddenException('Puoi seguire solo liste pubbliche');
        }
        if (list.follower_ids.includes(userId)) {
            throw new common_1.BadRequestException('Stai già seguendo questa lista');
        }
        list.follower_ids = [...list.follower_ids, userId];
        list.followers_count = list.follower_ids.length;
        await this.listRepository.save(list);
        return { message: 'Ora segui questa lista' };
    }
    async unfollowList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        list.follower_ids = list.follower_ids.filter((id) => id !== userId);
        list.followers_count = list.follower_ids.length;
        await this.listRepository.save(list);
        return { message: 'Non segui più questa lista' };
    }
    async getListFollowers(listId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('Lista non trovata');
        }
        if (list.follower_ids.length === 0) {
            return [];
        }
        return this.userRepository.find({
            where: { id: (0, typeorm_2.In)(list.follower_ids) },
        });
    }
    async getMoviesForList(movieIds) {
        if (!movieIds || movieIds.length === 0) {
            return [];
        }
        return this.movieRepository.find({
            where: { id: (0, typeorm_2.In)(movieIds) },
        });
    }
};
exports.ListsService = ListsService;
exports.ListsService = ListsService = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(list_entity_1.MovieListEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __param(2, (0, typeorm_1.InjectRepository)(user_entity_1.UserEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository,
        typeorm_2.Repository])
], ListsService);
//# sourceMappingURL=lists.service.js.map