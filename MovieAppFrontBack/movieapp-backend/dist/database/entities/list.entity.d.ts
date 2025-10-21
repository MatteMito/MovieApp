import { UserEntity } from './user.entity';
export declare class MovieListEntity {
    id: string;
    user_id: string;
    user: UserEntity;
    name: string;
    description?: string;
    movie_ids: string[];
    target_date?: Date;
    frequency?: string;
    is_public: boolean;
    followers_count: number;
    follower_ids: string[];
    created_at: Date;
    updated_at: Date;
}
