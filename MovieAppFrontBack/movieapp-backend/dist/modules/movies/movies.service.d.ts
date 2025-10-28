import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserEntity } from '../../database/entities/user.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { DatabaseService } from '../../database/database.service';
import { TmdbService } from '../tmdb/tmdb.service';
import { WebsocketGateway } from '../websocket/websocket.gateway';
import { Movie, BatchUploadResult, EnrichmentResult } from '../../common/interfaces/movie.interface';
export declare class MoviesService {
    private movieRepository;
    private userRepository;
    private userMovieRepository;
    private databaseService;
    private tmdbService;
    private websocketGateway;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>, userRepository: Repository<UserEntity>, userMovieRepository: Repository<UserMovieEntity>, databaseService: DatabaseService, tmdbService: TmdbService, websocketGateway: WebsocketGateway);
    healthCheck(): Promise<any>;
    getStats(): Promise<any>;
    getUserStats(userId: string): Promise<any>;
    getMovieById(id: string): Promise<MovieEntity | null>;
    getAllMovies(userId: string): Promise<MovieEntity[]>;
    deleteAllMovies(userId: string): Promise<{
        message: string;
        deletedCount: number;
    }>;
    getUserMovies(userId: string, filters: any): Promise<any>;
    searchMovies(filters: any): Promise<any>;
    enrichMovies(movies: Movie[]): Promise<EnrichmentResult>;
    batchUpload(watchlist: Movie[], watched: Movie[], userId?: string): Promise<BatchUploadResult>;
    batchUploadWithUserAssociation(userId: string, watchlist: Movie[], watched: Movie[]): Promise<any>;
    private sleep;
}
