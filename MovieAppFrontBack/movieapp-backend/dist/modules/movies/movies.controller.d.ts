import { MoviesService } from './movies.service';
import { Movie } from '../../common/interfaces/movie.interface';
interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    message?: string;
    timestamp: string;
    debug?: any;
}
export declare class MoviesController {
    private readonly moviesService;
    private readonly logger;
    constructor(moviesService: MoviesService);
    healthCheck(): Promise<ApiResponse>;
    getCacheStats(): Promise<ApiResponse>;
    initializeApp(userId?: string): Promise<ApiResponse>;
    enrichMovies(body: {
        movies: Movie[];
    }): Promise<ApiResponse>;
    batchUpload(body: {
        watchlist: Movie[];
        watched: Movie[];
        userId: string;
    }, headerUserId?: string): Promise<ApiResponse>;
    getUserMovies(userId: string, status?: string): Promise<ApiResponse>;
    getUserStats(userId: string): Promise<ApiResponse>;
    getAllMovies(userId?: string): Promise<ApiResponse>;
}
export {};
