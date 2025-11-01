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
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.is_public && list.user_id !== userId) {
            throw new common_1.ForbiddenException('non hai accesso a questa lista');
        }
        const movies = await this.getMoviesForList(list.movie_ids);
        return {
            ...list,
            movies,
        };
    }
    async getMoviesForList(movieIds) {
        if (!movieIds || movieIds.length === 0) {
            return [];
        }
        const movies = await this.movieRepository.find({
            where: { id: (0, typeorm_2.In)(movieIds) },
        });
        return movieIds
            .map(id => movies.find(m => m.id === id))
            .filter(m => m !== undefined);
    }
    async createList(userId, createListDto) {
        const user = await this.userRepository.findOne({ where: { id: userId } });
        if (!user) {
            throw new common_1.NotFoundException('utente non trovato');
        }
        if (createListDto.movie_ids && createListDto.movie_ids.length > 0) {
            const movies = await this.movieRepository.find({
                where: { id: (0, typeorm_2.In)(createListDto.movie_ids) },
            });
            if (movies.length !== createListDto.movie_ids.length) {
                throw new common_1.BadRequestException('alcuni film non esistono nel database');
            }
        }
        const list = this.listRepository.create({
            user_id: userId,
            name: createListDto.name,
            description: createListDto.description,
            is_public: createListDto.is_public || false,
            movie_ids: createListDto.movie_ids || [],
        });
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
        };
    }
    async updateList(listId, userId, updateListDto) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi modificare questa lista');
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
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
        };
    }
    async deleteList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi eliminare questa lista');
        }
        await this.listRepository.remove(list);
        return { message: 'lista eliminata con successo' };
    }
    async addMovieToList(listId, userId, movieId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi modificare questa lista');
        }
        const movie = await this.movieRepository.findOne({ where: { id: movieId } });
        if (!movie) {
            throw new common_1.NotFoundException(`film ${movieId} non trovato`);
        }
        if (list.movie_ids.includes(movieId)) {
            throw new common_1.BadRequestException('film gia presente nella lista');
        }
        list.movie_ids = [...list.movie_ids, movieId];
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
        };
    }
    async removeMovieFromList(listId, userId, movieId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (list.user_id !== userId) {
            throw new common_1.ForbiddenException('non puoi modificare questa lista');
        }
        if (!list.movie_ids.includes(movieId)) {
            throw new common_1.BadRequestException('film non presente nella lista');
        }
        list.movie_ids = list.movie_ids.filter(id => id !== movieId);
        const saved = await this.listRepository.save(list);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
        };
    }
    async followList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.is_public) {
            throw new common_1.ForbiddenException('puoi seguire solo liste pubbliche');
        }
        if (list.follower_ids && list.follower_ids.includes(userId)) {
            throw new common_1.BadRequestException('stai gia seguendo questa lista');
        }
        list.follower_ids = [...(list.follower_ids || []), userId];
        list.followers_count = list.follower_ids.length;
        const saved = await this.listRepository.save(list);
        return {
            message: 'lista seguita con successo',
            list: saved,
        };
    }
    async unfollowList(listId, userId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.follower_ids || !list.follower_ids.includes(userId)) {
            throw new common_1.BadRequestException('non stai seguendo questa lista');
        }
        list.follower_ids = list.follower_ids.filter(id => id !== userId);
        list.followers_count = list.follower_ids.length;
        const saved = await this.listRepository.save(list);
        return {
            message: 'lista non seguita piu',
            list: saved,
        };
    }
    async getListFollowers(listId) {
        const list = await this.listRepository.findOne({ where: { id: listId } });
        if (!list) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!list.follower_ids || list.follower_ids.length === 0) {
            return [];
        }
        const followers = await this.userRepository.find({
            where: { id: (0, typeorm_2.In)(list.follower_ids) },
            select: ['id', 'username', 'email'],
        });
        return followers;
    }
    async copyList(listId, userId, newName) {
        const originalList = await this.listRepository.findOne({
            where: { id: listId }
        });
        if (!originalList) {
            throw new common_1.NotFoundException('lista non trovata');
        }
        if (!originalList.is_public) {
            throw new common_1.ForbiddenException('puoi copiare solo liste pubbliche');
        }
        const copiedList = this.listRepository.create({
            user_id: userId,
            name: newName || `${originalList.name} (copia)`,
            description: originalList.description,
            is_public: false,
            movie_ids: [...originalList.movie_ids],
        });
        const saved = await this.listRepository.save(copiedList);
        const movies = await this.getMoviesForList(saved.movie_ids);
        return {
            ...saved,
            movies,
        };
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